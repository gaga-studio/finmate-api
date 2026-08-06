package com.gagastudio.finmate.diary;

import java.util.Optional;

/**
 * 그림을 만들어 주는 바깥 세계.
 *
 * 인터페이스로 끊어 둔다. 이유가 둘이다 —
 * 하나, 테스트가 진짜 API를 부르면 매번 돈이 들고 결과가 매번 다르다.
 * 둘, 이 자리는 언젠가 바뀐다. 파이프라인이 특정 업체에 붙어 있으면 안 된다.
 *
 * 제출과 수거를 나눈 것은 생성이 수 초 걸리기 때문이다. 한 호출로 묶으면 그 시간 동안
 * 스레드가 잡혀 있고, 서버가 재시작하면 진행 중이던 작업을 영영 못 찾는다.
 * 작업 id를 받아 두면 재시작 후에도 결과를 다시 찾아올 수 있다.
 */
public interface ArtProvider {

	/** 큐에 넣고 작업 id를 받는다. 그림을 기다리지 않는다. */
	String submit(String prompt);

	/**
	 * 결과를 확인한다.
	 *
	 * @return 아직 안 끝났으면 {@link Optional#empty()}, 끝났으면 이미지 바이트
	 * @throws ArtGenerationException 제공자가 실패로 끝냈을 때
	 */
	Optional<byte[]> poll(String jobId);

	class ArtGenerationException extends RuntimeException {
		public ArtGenerationException(String message) {
			super(message);
		}

		public ArtGenerationException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
