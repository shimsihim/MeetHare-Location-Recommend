package YeoksamStationExit1.locationRecommend.service;

import YeoksamStationExit1.locationRecommend.dto.request.FindAvgDistanceReqDto;
import YeoksamStationExit1.locationRecommend.dto.request.FindCenterCoordinatesReqDto;
import YeoksamStationExit1.locationRecommend.dto.response.GetAvgDistanceRespDto;
import YeoksamStationExit1.locationRecommend.dto.response.GetStationCoordinateResDto;
import YeoksamStationExit1.locationRecommend.dto.response.TransPathPerUserDto;
import YeoksamStationExit1.locationRecommend.entity.Station;
import YeoksamStationExit1.locationRecommend.repository.LocationRepository;
import YeoksamStationExit1.locationRecommend.dto.response.FindMyStationRespDto;
import YeoksamStationExit1.locationRecommend.repository.QLocationRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.locationtech.jts.geom.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LocationService {

    private final LocationRepository locationRepository;

    private final QLocationRepository QLocationRepository;

    @Value("${kakao.key}")
    private String kakaokey;
    private String kakaourl = "https://dapi.kakao.com/v2/local/search/category";

    @Value("${odsay.key}")
    private String odsaykey;
    private String odsayurl = "https://api.odsay.com/v1/api/searchPubTransPathT";

    @Value("${map.key}")
    private String mapkey;
    private String mapurl = "https://api.mapbox.com/isochrone/v1/mapbox/driving-traffic";
    private List<GetStationCoordinateResDto> list;

    /**
     * [2] 중심좌표 기준 가까운 지하철 역을 구하는 메서드
     * 외부 api (kakao api) 를 사용하여 목록을 받아옴.
     */
    public Set<String> findNearbyAreas(double[] centerCoordinates) {
        // 중심좌표
        double longitude = centerCoordinates[0];// 경도
        double latitude = centerCoordinates[1];// 위도

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.set("Authorization", "KakaoAK " + kakaokey); // Authorization 설정
        HttpEntity<String> httpEntity = new HttpEntity<>(httpHeaders);
        URI targetUrl = UriComponentsBuilder
                .fromUriString(kakaourl) // 기본 url
                .queryParam("category_group_code", "SW8") // 카테고리설정(지하철)
                .queryParam("x", longitude) // 경도
                .queryParam("y", latitude) // 위도
                .queryParam("radius", 1500) // 거리 m단위
                .build()
                .encode(StandardCharsets.UTF_8) // 인코딩
                .toUri();

        // GetForObject는 헤더를 정의할 수 없음
        ResponseEntity<Map> result = restTemplate.exchange(targetUrl, HttpMethod.GET, httpEntity, Map.class);
        return getStationNames(result);
    }

    /**
     * [3] api 를 사용하여 받아온 data 중 필요한 데이터만을 가져옴
     * ResponseEntity 중 역이름만 꺼내서 List에 담기
     */
    public Set<String> getStationNames(ResponseEntity<Map> responseEntity) {
        Map<String, Object> responseBody = responseEntity.getBody();

        // "documents" 키의 값을 가져와 List<Map> 형태로 캐스팅
        List<Map<String, Object>> documents = (List<Map<String, Object>>) responseBody.get("documents");

        // "place_name" 값을 추출하여 List<String>에 담기
        Set<String> placeNames = new HashSet<>(); // 역이름만 담은 리스트
        for (Map<String, Object> document : documents) {
            String placeName = (String) document.get("place_name");
            String[] words = placeName.split(" "); // 호선은 제외한 역이름만 사용
            if (words.length > 0) {
                placeNames.add(words[0]);
            }
        }

        // 결과 출력
        // for (String placeName : placeNames) {
        // System.out.println(placeName);
        // }
        return placeNames;
    }

    /**
     * [1] 사용자의 위치에 따른 무게중심 좌표를 구하는 메서드
     */
    public Set<String> findCenterCoordinates(List<FindCenterCoordinatesReqDto> req) {
        List<FindCenterCoordinatesReqDto> positions = req;
        double cnt = req.size(); // 사용자 수
        double sumOfLong = 0; // 경도
        double sumOfLat = 0; // 위도
        for (FindCenterCoordinatesReqDto fcc : positions) {
            sumOfLong += fcc.getLongitude();
            sumOfLat += fcc.getLatitude();
        }
        double[] centerCoordinates = new double[2];
        centerCoordinates[0] = Math.round((sumOfLong / cnt) * 100000000.0) / 100000000.0; // 경도
        centerCoordinates[1] = Math.round((sumOfLat / cnt) * 100000000.0) / 100000000.0; // 위도
        System.out.println("중심경도 : " + centerCoordinates[0]);
        System.out.println("중심위도 : " + centerCoordinates[1]);

        return findNearbyAreas(centerCoordinates);
    }

    public Station findPlaceByInfracount(Set<String> placeNames) {

        // Iterator<String> iter = placeNames.iterator();
        // List<String> test = new ArrayList<>();

        // while (iter.hasNext()) {
        // test.add(iter.next());
        // }

        // 인프라 순으로 가져오기
        List<Station> list = locationRepository.findByInfraCount(placeNames);
        /*
         * 차후 인프라 많은 곳, 적은 곳 선택하여 목적지 정하는 기능을 위해
         * infracount 순으로 리스트에 넣어 둠
         * 1차 배포를 위해서 가장 infracount가 많은 장소를 리턴
         *
         */
        return list.get(0);

    }

    public List<TransPathPerUserDto> searchPubTransPath(List<FindCenterCoordinatesReqDto> req,
                                                        Station recommendPlace) {

        List<TransPathPerUserDto> list = new ArrayList<>();

        for (FindCenterCoordinatesReqDto rq : req) {
            RestTemplate restTemplate = new RestTemplate();

            String apiUrl = odsayurl + "?lang=0&output=json"
                    + "&SX=" + rq.getLongitude()
                    + "&SY=" + rq.getLatitude()
                    + "&EX=" + recommendPlace.getLongitude()
                    + "&EY=" + recommendPlace.getLatitude()
                    + "&OPT=0&apiKey="
                    + URLEncoder.encode(odsaykey, StandardCharsets.UTF_8);

            URI uri = URI.create(apiUrl);
            String response = restTemplate.getForObject(uri, String.class);
            TransPathPerUserDto tpu = new TransPathPerUserDto(rq.getUserId(), rq.getLongitude(), rq.getLatitude(),
                    response);
            list.add(tpu);
        }

        return list;

    }

    /**
     * 역 이름을 기준으로 좌표를 검색하는 메서드
     */
    public List<FindMyStationRespDto> findMyStation(String stationName) {
        List<FindMyStationRespDto> stationList = QLocationRepository.findByStationName(stationName);
        for (FindMyStationRespDto dto : stationList) {
            System.out.println(dto.getStationName());
            System.out.println(dto.getLatitude());
            System.out.println(dto.getLongitude());
        }
        return stationList;
    }

    /**
     * db 좌표를 사용해 출발지 좌표와의 거리를 구하는 메서드 [1]
     * api 요청 형식 : 기본url/이동수단/경도,위도?contours_minutes=이동시간&access_token=토큰A&generalize=윤곽선단순화(max=500)
     */
    public double findAvgDistanceByTime() {
        double distance = 0.0;
        List<GetStationCoordinateResDto> list = QLocationRepository.findAll();
//        int stationId = list.get(1).getStationId();
//        double startLong = list.get(1).getLongitude();
//        double startLat = list.get(1).getLatitude();

//        for (int i = 10; i < 50; i++) {
//            int stationId = list.get(i).getStationId();
//            double startLong = list.get(i).getLongitude();
//            double startLat = list.get(i).getLatitude();

        for (GetStationCoordinateResDto dto : list) {
            int stationId = dto.getStationId();
            double startLong = dto.getLongitude();
            double startLat = dto.getLatitude();

            String jsonResponse = getMapByTime(startLong, startLat); //api 요청을 전송하여 등시선도 좌표를 받아오는 메서드
            distance = calAvgDistance(jsonResponse, startLong, startLat); //출발지와 모든 등시선도좌표상의 거리를 비표하여 평균거리를 구하는 메서드

            System.out.println("distance " + distance);
            if (Double.isNaN(distance)) {
                distance = 0.0; // 또는 다른 기본값으로 설정
            }
            QLocationRepository.updateDistanceColumn(stationId, distance);

            try {
                // 요청 간격을 조절하기 위한 딜레이
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return distance;
    }//

    /**
     * api 요청을 전송하여 등시선도 좌표를 받아오는 메서드 [1]
     * parameter: 출발지의 위경도 좌표
     * return: 응답된 json
     */
    public String getMapByTime(double startLong, double startLat) {
        RestTemplate restTemplate = new RestTemplate();
        URI targetUrl = UriComponentsBuilder
                .fromUriString(mapurl) // 기본 url
                .path("/{origin},{destination}")
                .queryParam("contours_minutes", "55") // 이동시간
                .queryParam("access_token", mapkey) // access token
                .queryParam("generalize", 500) //
                .buildAndExpand(startLong, startLat)
                .encode(StandardCharsets.UTF_8) // 인코딩
                .toUri();

        ResponseEntity<String> responseEntity = restTemplate.getForEntity(targetUrl, String.class);

        String jsonResponse = responseEntity.getBody();// JSON 응답 데이터를 문자열로 설정
        System.out.println(jsonResponse);
        return jsonResponse;
    }

    /**
     * 출발지와 모든 등시선조 좌표 사이의 거리를 비교하여 평균거리를 구하는 메서드[2]
     */
    public double calAvgDistance(String jsonResponse, double startLong, double startLat) {
        // Jackson ObjectMapper 생성
        ObjectMapper objectMapper = new ObjectMapper();
        double distance = 0.0;

        try {
            JsonNode jsonNode = objectMapper.readTree(jsonResponse);// JSON 문자열을 JsonNode로 파싱

            JsonNode coordinatesNode = jsonNode // "features" 배열에서 "coordinates" 배열 추출
                    .path("features")
                    .get(0) // 첫 번째 요소
                    .path("geometry")
                    .path("coordinates");

            // 좌표 값을 담을 리스트 생성
            Set<List<Double>> coordinatesList = new HashSet<>();

            double sumDistanceOfPoint = 0.0; //등시선도 좌표개수와 출발지사이의 거리 합
            // 좌표 배열을 리스트에 추가
            for (JsonNode coord : coordinatesNode) {
                double endLong = coord.get(0).asDouble(); //경도
                double endLat = coord.get(1).asDouble(); //위도
                sumDistanceOfPoint += calculateFlatDistance(startLat, startLong, endLat, endLong);
                List<Double> point = Arrays.asList(endLat, endLong); //위도경도순서
                coordinatesList.add(point);
            }
            // 좌표 리스트 출력
            double pointCnt = coordinatesList.size(); //등시선도좌표개수
            double avgDistance = (sumDistanceOfPoint / pointCnt) * 1000;
            DecimalFormat df = new DecimalFormat("#.######");
            String formattedNumber = df.format(avgDistance);
            distance = Double.parseDouble(formattedNumber);

        } catch (IOException e) {
            e.printStackTrace();
        }
        return distance;
    }

    /**
     * 출발지와 특정 등시선도 좌표사이의 거리를 구하는 메서드[3]
     */
    public double calculateFlatDistance(double lat1, double lon1, double lat2, double lon2) {
        // 평면 거리를 계산할 때 사용할 상수 (단위: km)
        final double kmPerDegreeLat = 111.32;
        final double kmPerDegreeLon = 111.32;

        // 위도 및 경도 간의 차이를 계산
        double latDiff = Math.abs(lat2 - lat1);
        double lonDiff = Math.abs(lon2 - lon1);

        // 평면 거리 계산
        double flatDistance = Math.sqrt(Math.pow(latDiff * kmPerDegreeLat, 2) + Math.pow(lonDiff * kmPerDegreeLon, 2));

        return flatDistance;
    }

    public void selectAll() {
        List<GetStationCoordinateResDto> list = QLocationRepository.findAll();

        int id = list.get(0).getStationId();
        System.out.println(id);
        double lat = list.get(0).getLatitude();
        System.out.println(lat);
        double log = list.get(0).getLongitude();
        System.out.println(log);

        QLocationRepository.updateDistanceColumn(id, 3);

//        for (GetStationCoordinateResDto dto : list ){
//            System.out.println(dto.getStationId());
//            System.out.println(dto.getLatitude());
//            System.out.println(dto.getLongitude());
//        }
    }

    /**
     * db등시선도 기준 이동가능한 범위 체크
     * */
    public void checkMovableArea(List<FindAvgDistanceReqDto> req) {

        //db데이터 접근을 위한 쿼리 생성
        GetAvgDistanceRespDto stationInfo1 = QLocationRepository.findByStationId(req.get(0).getStationId());
        GetAvgDistanceRespDto stationInfo2 = QLocationRepository.findByStationId(req.get(1).getStationId());

        //출발지 좌표
        Coordinate startPoint1 = new Coordinate(req.get(0).getLongitude(), req.get(0).getLatitude());
        Coordinate startPoint2 = new Coordinate(req.get(0).getLongitude(), req.get(0).getLatitude());

        // 거리 칼럼 설정
        int[] distancesByUser1 = {
                (int)stationInfo1.getMin5distance(),
                (int)stationInfo1.getMin10distance(),
                (int)stationInfo1.getMin15distance(),
                (int)stationInfo1.getMin20distance(),
                (int)stationInfo1.getMin25distance(),
                (int)stationInfo1.getMin30distance(),
                (int)stationInfo1.getMin35distance(),
                (int)stationInfo1.getMin40distance(),
                (int)stationInfo1.getMin45distance(),
                (int)stationInfo1.getMin50distance(),
                (int)stationInfo1.getMin55distance(),
                (int)stationInfo1.getMin60distance()
        };
        int[] distancesByUser2 = {
                (int)stationInfo2.getMin5distance(),
                (int)stationInfo2.getMin10distance(),
                (int)stationInfo2.getMin15distance(),
                (int)stationInfo2.getMin20distance(),
                (int)stationInfo2.getMin25distance(),
                (int)stationInfo2.getMin30distance(),
                (int)stationInfo2.getMin35distance(),
                (int)stationInfo2.getMin40distance(),
                (int)stationInfo2.getMin45distance(),
                (int)stationInfo2.getMin50distance(),
                (int)stationInfo2.getMin55distance(),
                (int)stationInfo2.getMin60distance()
        };

        GeometryFactory geometryFactory = new GeometryFactory();

        for (int i = 0; i < 12; i++) {
            // 원을 그리기 위한 반경 계산 (단위: 도)
            double radius1 = distancesByUser1[i] / 111.32;
            double radius2 = distancesByUser2[i] / 111.32;

            // 출발지 좌표를 Point 객체로 생성
            Point point1 = geometryFactory.createPoint(startPoint1);
            Point point2 = geometryFactory.createPoint(startPoint2);

            // 원을 그리기 위한 Polygon 생성
            Polygon circle1 = (Polygon) point1.buffer(radius1);
            Polygon circle2 = (Polygon) point2.buffer(radius2);


            // 두 원이 교차하는지 확인
            Geometry intersection = circle1.intersection(circle2);

            if (!intersection.isEmpty()) {
                Coordinate[] intersectionCoordinates = intersection.getCoordinates();
                System.out.println("교차점이 있습니다. "+i+" 유저1반지름: " + distancesByUser1[i] + " km");
                System.out.println("교차점이 있습니다. "+i+" 유저2반지름: " + distancesByUser2[i] + " km");
                System.out.println("교차 좌표: ");
                for (Coordinate coordinate : intersectionCoordinates) {
                    System.out.println("Latitude: " + coordinate.y + ", Longitude: " + coordinate.x);
                }
                break;
            } else {
                System.out.println("교차점이 없습니다. 반지름: " + distancesByUser1[i] + " km");
            }



        }



    }


}
