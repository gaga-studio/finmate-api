-- 가입한 사람에게 합성 인구 한 명을 붙인다.
--
-- 이 서비스는 마이데이터를 연동해 진짜 거래를 받아오는 것이 전제다. 지금은 그 연동이 없으므로,
-- 가입하면 합성 인구 중 아직 배정되지 않은 한 명을 준다. 그래야 로그인 직후 화면이 빈 채로 뜨지 않는다.
--
-- persona_id는 V2에서 이미 추가했다. 여기서는 "한 사람을 두 계정이 나눠 갖지 않는다"만 건다 —
-- 같은 원장을 두 사람이 보면 또래 비교 인원 수가 어긋난다.
CREATE UNIQUE INDEX uq_user_persona ON finmate_user (persona_id) WHERE persona_id IS NOT NULL;
