package com.example.amaliyot.repository;

import com.example.amaliyot.domain.Entities.UserAccount;
import com.example.amaliyot.domain.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long>, JpaSpecificationExecutor<UserAccount> {
    Optional<UserAccount> findByPhone(String phone);
    Optional<UserAccount> findByEmailIgnoreCase(String email);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserAccount> findLockedByPhone(String phone);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<UserAccount> findLockedByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    boolean existsByPhoneAndIdNot(String phone, Long id);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPasswordHash(String passwordHash);
    long countByActiveTrue();
    boolean existsByRole(Role role);
    Optional<UserAccount> findFirstByRole(Role role);
    long countByRole(Role role);
    long countByRoleAndActiveTrue(Role role);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UserAccount u where u.id = :id")
    Optional<UserAccount> findByIdForUpdate(@Param("id") Long id);
}
