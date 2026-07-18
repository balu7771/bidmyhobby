package com.bidmyhobby.catalog.repository;

import com.bidmyhobby.catalog.model.SmallBusiness;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SmallBusinessRepository extends JpaRepository<SmallBusiness, Long> {
    
    Page<SmallBusiness> findByIsApprovedTrueAndIsActiveTrue(Pageable pageable);
    
    List<SmallBusiness> findByIsApprovedTrueAndIsActiveTrue();
    
    Page<SmallBusiness> findByIsApprovedTrueAndIsActiveTrueAndCategoryContainingIgnoreCase(
            String category, Pageable pageable);
    
    Page<SmallBusiness> findByIsApprovedTrueAndIsActiveTrueAndLocationContainingIgnoreCase(
            String location, Pageable pageable);
    
    @Query("SELECT sb FROM SmallBusiness sb WHERE sb.isApproved = true AND sb.isActive = true " +
           "AND (LOWER(sb.businessName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(sb.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           "OR LOWER(sb.category) LIKE LOWER(CONCAT('%', :keyword, '%')))")
    Page<SmallBusiness> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);
    
    List<SmallBusiness> findByUploaderEmailAndIsActiveTrue(String uploaderEmail);
    
    List<SmallBusiness> findByIsApprovedFalseAndIsActiveTrue();
    
    long countByIsApprovedTrueAndIsActiveTrue();
}