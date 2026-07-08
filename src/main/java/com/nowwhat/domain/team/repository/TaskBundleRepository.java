package com.nowwhat.domain.team.repository;

import com.nowwhat.domain.team.entity.TaskBundle;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskBundleRepository extends JpaRepository<TaskBundle, Long> {
    List<TaskBundle> findByTeam_Id(Long teamId);
}
