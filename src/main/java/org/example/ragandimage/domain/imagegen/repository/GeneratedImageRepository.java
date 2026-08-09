package org.example.ragandimage.domain.imagegen.repository;

import org.example.ragandimage.domain.imagegen.entity.GeneratedImage;
import org.springframework.data.jpa.repository.JpaRepository;

// [Step 3-5] GeneratedImage JPA 리포지토리
public interface GeneratedImageRepository extends JpaRepository<GeneratedImage, Long> {
}
