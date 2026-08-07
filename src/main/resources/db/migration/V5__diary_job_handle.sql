-- 작업 손잡이에 상태·결과 주소를 함께 넣는다.
--
-- 처음엔 작업 id만 저장하고 조회 주소를 직접 조립했다. 405를 받았다 —
-- 제출은 fal-ai/flux/dev인데 조회 주소는 fal-ai/flux/requests/{id}로 내려온다.
-- 모델 경로에서 변형(dev)이 빠지는 규칙을 몰랐던 것이다.
-- 제공자가 준 주소를 그대로 들고 다니면 규칙을 알 필요가 없다.
ALTER TABLE diary_entry ALTER COLUMN provider_job_id TYPE VARCHAR(400);
