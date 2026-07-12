package com.gagastudio.finmate.mate;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

interface MateGroupRepository extends JpaRepository<MateGroup, String> {
	List<MateGroup> findAllByOrderById();
}
