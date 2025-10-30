package org.spacehub.repository.community;

import org.spacehub.entities.Community.Community;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommunityRepository extends JpaRepository<Community, Long> {

  Community findByName(String name);
  @NonNull
  List<Community> findAll();

}
