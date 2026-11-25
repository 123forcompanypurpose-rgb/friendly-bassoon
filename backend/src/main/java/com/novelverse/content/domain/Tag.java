package com.novelverse.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "tags")
public class Tag {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 40)
  private String name;

  @Column(nullable = false, length = 50)
  private String slug;

  @Column(name = "use_count", nullable = false)
  private int useCount;

  protected Tag() {}

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public int getUseCount() {
    return useCount;
  }
}
