package com.example.inventory.users;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findByUsernameIgnoreCase(String username);

    @EntityGraph(attributePaths = "roles")
    Optional<UserAccount> findByEmailIgnoreCase(String email);

    @Query("select distinct account from UserAccount account left join fetch account.roles "
            + "where account.id = :id")
    Optional<UserAccount> findByIdWithRoles(@Param("id") UUID id);

    @EntityGraph(attributePaths = "roles")
    List<UserAccount> findAllByOrderByUsernameAsc();

    @Query("select distinct account from UserAccount account left join fetch account.roles "
            + "order by account.username")
    List<UserAccount> findAllWithRoles();

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    @Query("""
            select count(account) > 0 from UserAccount account join account.roles role
            where role.name = :role
            """)
    boolean existsWithRole(@Param("role") RoleName role);

    @Query("""
            select count(distinct account) from UserAccount account join account.roles role
            where role.name = :role and account.enabled = true and account.locked = false
            """)
    long countActiveWithRole(@Param("role") RoleName role);
}
