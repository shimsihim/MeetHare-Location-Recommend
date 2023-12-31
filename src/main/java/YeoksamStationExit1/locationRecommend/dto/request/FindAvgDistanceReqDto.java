package YeoksamStationExit1.locationRecommend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class FindAvgDistanceReqDto {
    int stationId;
    double longitude;
    double latitude;
    String userId;
}
