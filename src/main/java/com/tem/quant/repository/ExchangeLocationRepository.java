package com.tem.quant.repository;

import com.tem.quant.entity.ExchangeLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ExchangeLocationRepository extends JpaRepository<ExchangeLocation, Long> {

    // 1. 거래소 이름으로 찾기 (예: Upbit)
    Optional<ExchangeLocation> findByExchangeName(String exchangeName);

    // 2. 특정 국가 코드에 해당하는 모든 거래소 위치 가져오기 (예: KR, US)
    List<ExchangeLocation> findAllByCountryCode(String countryCode);

    // 3. (지도용) 위도와 경도 데이터가 존재하는 것만 필터링해서 가져오기
    List<ExchangeLocation> findByLatitudeIsNotNullAndLongitudeIsNotNull();
    
    // 4. 특정 지역(클러스터링용) 내의 거래소 검색이 필요할 때 활용 가능
    // List<ExchangeLocation> findByLatitudeBetweenAndLongitudeBetween(Double minLat, Double maxLat, Double minLng, Double maxLng);
}