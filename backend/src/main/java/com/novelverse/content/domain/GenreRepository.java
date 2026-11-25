package com.novelverse.content.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GenreRepository extends JpaRepository<Genre, Short> {

  List<Genre> findByActiveTrueOrderBySortOrderAscNameAsc();
}
