package com.gagastudio.finmate.ledger;

import java.io.IOException;
import java.io.PushbackReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * finmate-data가 만든 번들 하나를 읽는다.
 *
 * 번들 = `profile.json`(인구통계·금융 성향) + `ledger.csv`(거래 원장).
 * 데이터셋은 저장소에 없고 파이프라인으로 재생성한다(SEED=20260713, 결정적):
 *
 * <pre>
 *   cd finmate-data &amp;&amp; python3 pipeline/05_generate.py
 * </pre>
 *
 * 처음엔 CSV를 직접 잘랐다. "우리 파이프라인의 출력이라 형식이 고정"이라고 봤기 때문이다.
 * 틀렸다 — 가맹점명에 줄바꿈이 들어간 행이 있었고(예: P0010의 "신한은행 청년우대형
 * 주택청약종합저축\n..."), 줄 단위로 읽던 파서가 필드 수를 6개로 세면서 적재가 통째로 깨졌다.
 * 형식이 고정이라는 말과 파싱이 쉽다는 말은 다르다. commons-csv에 맡긴다.
 */
final class BundleReader {

	private static final ObjectMapper JSON = new ObjectMapper();

	private BundleReader() {
	}

	static BundleRows.Bundle read(Path bundleDir) {
		BundleRows.PersonaRow persona = readProfile(bundleDir.resolve("profile.json"));
		return new BundleRows.Bundle(persona, readLedger(bundleDir.resolve("ledger.csv"), persona.id()));
	}

	private static BundleRows.PersonaRow readProfile(Path path) {
		try {
			JsonNode p = JSON.readTree(path.toFile());
			String externalId = p.get("persona_id").asText();

			// persona_id에서 결정적으로 UUID를 만든다. 데이터셋을 다시 적재해도 같은 id가 나와야
			// finmate_user.persona_id 연결이 끊기지 않는다.
			UUID id = UUID.nameUUIDFromBytes(("finmate-persona:" + externalId).getBytes(StandardCharsets.UTF_8));

			// "2026-01~2026-07" — 월만 있으므로 시작월 1일과 끝월 말일로 편다
			String[] range = p.get("data_range").asText().split("~");
			LocalDate from = LocalDate.parse(range[0].trim() + "-01");
			LocalDate to = LocalDate.parse(range[1].trim() + "-01").plusMonths(1).minusDays(1);

			return new BundleRows.PersonaRow(
				id, externalId, p.get("synthetic_name").asText(),
				(short) p.get("age").asInt(), p.get("cohort").asText(), p.get("job").asText(),
				p.get("archetype").asText(), p.get("region").asText(),
				p.get("household_type").asText(), (short) p.get("household_size").asInt(),
				p.get("monthly_income_krw").asLong(), p.get("income_band").asText(),
				p.get("income_regularity").asText(), p.get("target_monthly_spend_krw").asLong(),
				BigDecimal.valueOf(p.get("target_saving_rate").asDouble()),
				BigDecimal.valueOf(p.get("target_investment_rate").asDouble()),
				p.get("invest_participation").asBoolean(),
				(short) p.get("risk_score").asInt(), p.get("risk_attitude").asText(),
				from, to);
		} catch (IOException e) {
			throw new UncheckedIOException("프로필을 읽지 못했습니다: " + path, e);
		}
	}

	private static List<BundleRows.LedgerRow> readLedger(Path path, UUID personaId) {
		List<BundleRows.LedgerRow> rows = new ArrayList<>();
		// BOM을 남기면 첫 컬럼명이 "\uFEFF날짜"가 되어 헤더 조회가 전부 빗나간다.
		CSVFormat format = CSVFormat.DEFAULT.builder()
			.setHeader()
			.setSkipHeaderRecord(true)
			.setIgnoreSurroundingSpaces(true)
			.build();

		try (var reader = skipBom(Files.newBufferedReader(path, StandardCharsets.UTF_8));
			var parser = format.parse(reader)) {
			for (CSVRecord r : parser) {
				String flow = r.get("cashflow_bucket");
				String major = r.get("대분류");
				String minor = r.get("소분류");

				rows.add(new BundleRows.LedgerRow(
					personaId,
					r.get("transaction_id"),
					LocalDateTime.of(LocalDate.parse(r.get("날짜")), LocalTime.parse(r.get("시간"))),
					r.get("내용"),
					Long.parseLong(r.get("금액")),
					LedgerCategory.of(flow, major, minor).wireName(),
					flow, major, minor,
					blankToNull(r.get("rule_id")),
					blankToNull(r.get("결제수단")),
					blankToNull(r.get("메모"))));
			}
		} catch (IOException e) {
			throw new UncheckedIOException("원장을 읽지 못했습니다: " + path, e);
		}
		return rows;
	}

	/**
	 * UTF-8 BOM을 걷어낸다. 남겨두면 첫 컬럼명이 "\uFEFF날짜"가 되어 헤더 조회가 전부 빗나간다.
	 * 한 글자만 앞을 보고, BOM이 아니면 되돌려 놓는다.
	 */
	private static Reader skipBom(Reader reader) throws IOException {
		PushbackReader pushback = new PushbackReader(reader, 1);
		int first = pushback.read();
		if (first != -1 && first != 0xFEFF) {
			pushback.unread(first);
		}
		return pushback;
	}

	private static String blankToNull(String v) {
		return v == null || v.isBlank() ? null : v;
	}
}
