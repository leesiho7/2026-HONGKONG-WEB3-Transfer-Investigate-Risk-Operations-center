package com.tem.quant.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.tem.quant.entity.AddressLabel;

@Repository
public interface AddressLabelRepository extends JpaRepository<AddressLabel, String> {

    Optional<AddressLabel> findByAddress(String address);

    List<AddressLabel> findByGroupName(String groupName);

    // category는 String으로 통일 ("EXCHANGE", "HACKER" 등)
    List<AddressLabel> findByCategory(String category);
}

