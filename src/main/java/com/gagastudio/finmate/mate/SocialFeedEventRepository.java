package com.gagastudio.finmate.mate;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SocialFeedEventRepository extends JpaRepository<SocialFeedEvent, UUID> {
	List<SocialFeedEvent> findAllByOrderByDisplayOrder();
}
