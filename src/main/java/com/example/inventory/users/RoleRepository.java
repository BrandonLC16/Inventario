package com.example.inventory.users;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByName(RoleName name);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select role from Role role where role.name = :name")
    Optional<Role> findByNameForUpdate(@Param("name") RoleName name);

    Set<Role> findAllByNameIn(Set<RoleName> names);

    List<Role> findAllByNameIn(Collection<RoleName> names);
}
