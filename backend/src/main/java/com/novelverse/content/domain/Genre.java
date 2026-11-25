package com.novelverse.content.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "genres")
public class Genre {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Short id;

  @Column(nullable = false, length = 50)
  private String name;

  @Column(nullable = false, length = 60)
  private String slug;

  @Column(name = "sort_order", nullable = false)
  private int sortOrder;

  @Column(nullable = false)
  private boolean active;

  protected Genre() {}

  public Short getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public String getSlug() {
    return slug;
  }

  public int getSortOrder() {
    return sortOrder;
  }

  public boolean isActive() {
    return active;
  }
}
