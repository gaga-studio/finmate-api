package com.gagastudio.finmate.diary;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 워커를 주기적으로 돌린다.
 *
 * 스케줄러와 워커를 나눈 이유는 테스트다. 워커를 직접 부를 수 있어야 상태 전이를 한 걸음씩
 * 확인할 수 있고, 시간이 흐르기를 기다리는 테스트는 느리고 흔들린다.
 *
 * 기본으로 꺼 둔다. 테스트가 뜰 때마다 배경에서 작업을 집어가면, 테스트가 기대한 상태와
 * 스케줄러가 만든 상태가 엇갈려 원인 모를 실패가 난다.
 */
@Component
@ConditionalOnProperty(name = "finmate.art.scheduler", havingValue = "true")
public class DiaryScheduler {

	private static final int BATCH = 20;

	private final DiaryWorker worker;

	public DiaryScheduler(DiaryWorker worker) {
		this.worker = worker;
	}

	/** 생성이 3~6초라 그보다 촘촘히 볼 이유가 없다. */
	@Scheduled(fixedDelayString = "${finmate.art.interval-ms:3000}")
	public void tick() {
		worker.tick(BATCH);
	}
}
