package com.gagastudio.finmate.mate;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface SocialFriendRepository extends JpaRepository<SocialFriend, String> {
	List<SocialFriend> findAllByOrderByDisplayOrder();
	long countByQuestCompletedTodayTrue();
}
