package YeoksamStationExit1.locationRecommend.repository;

import YeoksamStationExit1.locationRecommend.entity.Station;
import YeoksamStationExit1.locationRecommend.entity.StationTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public interface StationTimeRepository extends JpaRepository<StationTime, Integer> {

    @Query(value = "SELECT * FROM station_time WHERE status = 'N' LIMIT 15 OFFSET ?1", nativeQuery = true)
    List<StationTime> findStationTimeByIntGreaterThanEqual(@Param("offset") int offset);

    @Modifying
    @Query(value = "UPDATE station_time SET travel_time = ?2, STATUS = 'Y' WHERE station_time_id = ?1", nativeQuery = true)
    int updateStatusStation(int stationId, int travelTime);

    @Query(value = "SELECT s FROM Station s WHERE s.stationName IN (SELECT st.endStation FROM StationTime st " +
            "WHERE st.startStation = :startStation " +
            "AND st.status = 'Y'" +
            "AND st.travelTime < :maxTravelTime " +
            ") ORDER BY s.infraCount DESC")
    List<Station> findStationByStationTime(@Param("startStation") String startStation, @Param("maxTravelTime") int maxTravelTime);

}
