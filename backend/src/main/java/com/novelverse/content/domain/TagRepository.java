package com.novelverse.content.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TagRepository extends JpaRepository<Tag, Long> {

  Optional<Tag> findByNameIgnoreCase(String name);

  Optional<Tag> findBySlug(String slug);

  List<Tag> findTop20ByNameContainingIgnoreCaseOrderByUseCountDescNameAsc(String name);
}
