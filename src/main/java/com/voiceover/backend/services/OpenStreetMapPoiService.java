package com.voiceover.backend.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceover.backend.entities.Poi;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
public class OpenStreetMapPoiService {
    public static final String SAIGON_UNIVERSITY_NARRATION = """
            Xin kính chào quý khách! Chào mừng quý khách đến với Trường Đại học Sài Gòn, một trong những cơ sở giáo dục đại học công lập tại Thành phố Hồ Chí Minh, nơi gắn liền với hoạt động đào tạo, nghiên cứu khoa học và sự phát triển của nhiều thế hệ sinh viên.

            Trường Đại học Sài Gòn chính thức được thành lập vào ngày 25 tháng 4 năm 2007, trên cơ sở nâng cấp Trường Cao đẳng Sư phạm Thành phố Hồ Chí Minh. Trụ sở chính của trường tọa lạc tại số 273 An Dương Vương, thuộc khu vực trung tâm có truyền thống giáo dục lâu đời của thành phố.

            Mặc dù mang tên gọi hiện nay từ năm 2007, cơ sở đào tạo này có quá trình hình thành và phát triển gắn với hoạt động giáo dục từ nhiều năm trước đó. Trường từng bước mở rộng quy mô, phát triển đội ngũ giảng viên và đa dạng hóa các chương trình đào tạo nhằm đáp ứng nhu cầu nhân lực của xã hội.

            Hiện nay, Trường Đại học Sài Gòn đào tạo nhiều lĩnh vực như sư phạm, công nghệ thông tin, kinh tế, quản trị, khoa học tự nhiên và khoa học xã hội. Bên cạnh nhiệm vụ giảng dạy, nhà trường còn chú trọng nghiên cứu khoa học, hợp tác giáo dục và các hoạt động phục vụ cộng đồng.

            Khi đến đây, quý khách có thể cảm nhận không khí học tập năng động của sinh viên qua những khu giảng đường, không gian sinh hoạt và các hoạt động học thuật. Đây không chỉ là nơi truyền đạt kiến thức mà còn là môi trường để sinh viên rèn luyện kỹ năng, phát triển tư duy và chuẩn bị cho hành trình nghề nghiệp tương lai.

            Với vai trò là một cơ sở giáo dục đại học tại Thành phố Hồ Chí Minh, Trường Đại học Sài Gòn góp phần đào tạo nguồn nhân lực và thúc đẩy sự phát triển của giáo dục địa phương.

            Cảm ơn quý khách đã cùng VoiceMap tìm hiểu về Trường Đại học Sài Gòn. Chúc quý khách có thêm những trải nghiệm thú vị trong hành trình khám phá thành phố!
            """.replace("\n\n", " ").trim();
    public static final String SEPTEMBER_23_PARK_NARRATION = """
            Xin kính chào quý khách! Chào mừng quý khách đến với Công viên 23 Tháng 9, một không gian công cộng quen thuộc nằm tại khu vực trung tâm Thành phố Hồ Chí Minh.

            Công viên nằm gần những tuyến đường sầm uất như Phạm Ngũ Lão, Lê Lai và khu vực Chợ Bến Thành. Nhờ vị trí thuận lợi, nơi đây trở thành điểm dừng chân được nhiều người dân và du khách lựa chọn trong quá trình di chuyển, tham quan và khám phá thành phố.

            Khu vực công viên ngày nay có lịch sử gắn liền với sự biến đổi không gian đô thị Sài Gòn qua nhiều thời kỳ. Đây từng là khu vực liên quan đến các công trình giao thông đường sắt trước khi được chuyển đổi và phát triển thành không gian công cộng phục vụ người dân.

            Điều đầu tiên quý khách có thể cảm nhận khi đến đây là sự tương phản thú vị giữa nhịp sống đô thị nhộn nhịp và những khoảng xanh dành cho hoạt động nghỉ ngơi. Xung quanh công viên là các tuyến đường đông đúc, cửa hàng, khách sạn và khu phố du lịch, nhưng không gian cây xanh vẫn tạo nên những khoảng nghỉ trong hành trình khám phá thành phố.

            Vào những thời điểm thích hợp trong ngày, người dân thường đến công viên để đi bộ, tập thể dục, trò chuyện hoặc đơn giản là thư giãn sau giờ làm việc. Du khách cũng có thể lựa chọn nơi đây làm điểm xuất phát để tìm hiểu các địa danh nổi tiếng ở trung tâm thành phố.

            Từ khu vực Công viên 23 Tháng 9, quý khách có thể tiếp tục khám phá Chợ Bến Thành, các tuyến phố thương mại và nhiều điểm tham quan văn hóa nằm trong khoảng cách không quá xa.

            Công viên không chỉ góp phần tạo nên cảnh quan xanh mà còn phản ánh một phần đời sống sinh hoạt cộng đồng của người dân thành phố.

            Cảm ơn quý khách đã cùng VoiceMap tìm hiểu về Công viên 23 Tháng 9. Chúc quý khách có những phút giây thư giãn và một hành trình tham quan thật thú vị!
            """.replace("\n\n", " ").trim();
    public static final String AN_DONG_MARKET_NARRATION = """
            Xin kính chào quý khách! Chào mừng quý khách đến với Chợ An Đông, một trong những khu chợ thương mại truyền thống nổi tiếng tại Thành phố Hồ Chí Minh, đặc biệt được biết đến với hoạt động kinh doanh thời trang, quần áo, vải vóc và nhiều mặt hàng tiêu dùng.

            Chợ An Đông tọa lạc trên đường An Dương Vương, thuộc khu vực Quận 5 trước đây, nơi có lịch sử giao thương lâu đời và là một trong những trung tâm buôn bán sầm uất của thành phố.

            Được hình thành và phát triển từ giữa thế kỷ hai mươi, Chợ An Đông đã trở thành địa điểm mua bán quen thuộc của nhiều thế hệ tiểu thương. Không chỉ phục vụ nhu cầu mua sắm của người dân địa phương, khu chợ còn thu hút các thương nhân đến tìm kiếm nguồn hàng để kinh doanh tại nhiều tỉnh, thành khác.

            Điểm đặc trưng của Chợ An Đông là hoạt động kinh doanh hàng thời trang và may mặc. Khi bước vào bên trong, quý khách có thể bắt gặp những gian hàng trưng bày quần áo, giày dép, túi xách, phụ kiện và nhiều sản phẩm đa dạng về kiểu dáng, màu sắc và chất liệu.

            Bên cạnh các mặt hàng thời trang, khu chợ còn có những sản phẩm phục vụ đời sống hằng ngày. Không khí trao đổi, mua bán và vận chuyển hàng hóa tạo nên một nhịp sống thương mại năng động, phản ánh nét đặc trưng của các khu chợ truyền thống tại Sài Gòn.

            Một trong những điều thú vị khi tham quan Chợ An Đông chính là cơ hội quan sát văn hóa kinh doanh của các tiểu thương. Từ cách giới thiệu sản phẩm, trao đổi giá cả cho đến việc duy trì mối quan hệ với khách hàng, mỗi hoạt động đều góp phần tạo nên bản sắc riêng của khu chợ.

            Đối với du khách yêu thích khám phá đời sống địa phương, Chợ An Đông không chỉ đơn thuần là nơi mua sắm mà còn là địa điểm để tìm hiểu về sự phát triển thương mại và ngành kinh doanh thời trang tại Thành phố Hồ Chí Minh.

            Nếu có dịp ghé thăm, quý khách hãy dành thời gian dạo quanh các khu vực kinh doanh, quan sát sự đa dạng của hàng hóa và cảm nhận không khí buôn bán đặc trưng. Khi mua sắm, quý khách nên hỏi rõ giá cả và kiểm tra sản phẩm trước khi quyết định.

            Cảm ơn quý khách đã đồng hành cùng VoiceMap trong hành trình khám phá Chợ An Đông. Hy vọng điểm dừng chân này sẽ mang đến cho quý khách những trải nghiệm thú vị về văn hóa thương mại và đời sống thường ngày của người dân Sài Gòn.
            """.replace("\n\n", " ").trim();
    public static final String CHO_QUAN_CHURCH_NARRATION = """
            Xin kính chào quý khách! Chào mừng quý khách đến với Nhà thờ Chợ Quán, một trong những nhà thờ Công giáo lâu đời tại Thành phố Hồ Chí Minh, mang đậm dấu ấn lịch sử, kiến trúc và đời sống văn hóa của vùng đất Sài Gòn – Chợ Lớn.

            Nhà thờ tọa lạc tại số 120 Trần Bình Trọng, thuộc khu vực Quận 5 trước đây. Đây là địa điểm gắn liền với quá trình hình thành và phát triển của cộng đồng Công giáo tại Sài Gòn từ nhiều thế kỷ trước.

            Theo lịch sử giáo xứ, cộng đoàn Chợ Quán được hình thành từ khoảng năm 1722. Trải qua thời gian cùng nhiều biến động lịch sử, nhà thờ đã được xây dựng và trùng tu qua nhiều giai đoạn. Công trình hiện nay được xây dựng vào cuối thế kỷ mười chín, mang phong cách kiến trúc Gothic đặc trưng của các nhà thờ châu Âu.

            Khi quan sát từ bên ngoài, quý khách có thể nhận thấy tháp chuông cao vươn lên nổi bật, những mái vòm nhọn, hệ thống cửa sổ dài và các đường nét kiến trúc tạo cảm giác thanh thoát, trang nghiêm.

            Bước vào bên trong, không gian nhà thờ gây ấn tượng bởi chiều cao của trần, các hàng cột, những ô cửa kính màu và khu vực cung thánh được bố trí trang trọng. Ánh sáng xuyên qua các cửa kính góp phần tạo nên một không gian yên tĩnh, thích hợp cho các hoạt động cầu nguyện và suy ngẫm.

            Không chỉ là một công trình kiến trúc có giá trị, Nhà thờ Chợ Quán còn là nơi sinh hoạt tôn giáo của nhiều thế hệ giáo dân. Những buổi thánh lễ, các dịp lễ Giáng sinh, Phục sinh và những hoạt động cộng đồng đã góp phần duy trì đời sống tinh thần của giáo xứ.

            Đối với du khách, Nhà thờ Chợ Quán là một điểm đến thú vị để tìm hiểu về lịch sử hình thành các cộng đồng tôn giáo, sự giao thoa kiến trúc Đông – Tây và những dấu ấn văn hóa của Sài Gòn qua nhiều thời kỳ.

            Nếu có dịp tham quan, quý khách hãy dành thời gian chiêm ngưỡng tháp chuông, các chi tiết kiến trúc và không gian trang nghiêm bên trong. Trong quá trình tham quan, xin vui lòng giữ trật tự, ăn mặc lịch sự và tránh làm ảnh hưởng đến các hoạt động tôn giáo.

            Cảm ơn quý khách đã cùng VoiceMap khám phá Nhà thờ Chợ Quán. Hy vọng điểm dừng chân này sẽ mang đến cho quý khách những hiểu biết thú vị về lịch sử, kiến trúc và đời sống văn hóa của Thành phố Hồ Chí Minh.
            """.replace("\n\n", " ").trim();
    public static String englishNarrationFor(String name) {
        return switch (name) {
            case "Đại học Sài Gòn" -> """
                    Welcome to Saigon University, a public higher education institution in Ho Chi Minh City. The university is closely connected with teaching, scientific research, and the development of many generations of students.
                    Saigon University was officially established on April 25, 2007, after the former Ho Chi Minh City College of Education was upgraded. Its main campus is located at 273 An Duong Vuong Street, in an area with a long educational tradition.
                    The institution has continued to expand its academic programs, teaching staff, and community activities. Today, its fields include teacher education, information technology, economics, business administration, natural sciences, and social sciences.
                    As you look around, notice the campus entrance, the lecture buildings, the trees, and the rhythm of student life. This is not only a place for learning, but also an environment where students develop skills, independent thinking, and preparation for their future careers.
                    Saigon University contributes to education and human-resource development in Ho Chi Minh City. Thank you for exploring this landmark with VoiceMap. You may continue listening, pause the narration, or choose the suggested route when you are ready.
                    """.replace("\n", " ").trim();
            case "Công viên 23 tháng 9" -> """
                    Welcome to September 23 Park, a familiar public space in central Ho Chi Minh City. The park is close to busy streets such as Pham Ngu Lao, Le Lai, and the Ben Thanh Market area, making it a convenient stop for residents and visitors.
                    The site reflects the changing urban history of Saigon. An area once connected with railway transportation has been transformed into a public green space for the community.
                    Here you can notice an interesting contrast between busy city traffic and quiet areas for rest. People come to walk, exercise, meet friends, or relax after work, while visitors use the park as a starting point for exploring central landmarks.
                    From September 23 Park, you can continue toward Ben Thanh Market, shopping streets, and cultural attractions nearby. The park adds greenery to the city and reflects the everyday community life of Ho Chi Minh City. Thank you for discovering it with VoiceMap.
                    """.replace("\n", " ").trim();
            case "Chợ An Đông" -> """
                    Welcome to An Dong Market, one of Ho Chi Minh City's well-known traditional commercial markets. It is especially known for fashion, clothing, fabrics, accessories, and many everyday products.
                    The market is located on An Duong Vuong Street in the former District 5, an area with a long history of trade. Since the middle of the twentieth century, An Dong Market has served generations of local vendors and customers, as well as traders looking for products to sell in other provinces.
                    Inside, visitors can observe stalls displaying clothes, shoes, bags, and accessories in many styles, colors, and materials. The movement of customers, vendors, and goods creates a lively atmosphere that represents traditional market culture in Saigon.
                    An Dong Market is more than a shopping destination. It is also a place to observe local business customs, negotiations, and relationships between vendors and customers. Please ask about prices, check products carefully, and respect the people working here.
                    Thank you for exploring An Dong Market with VoiceMap. We hope this stop gives you a closer look at the commercial culture and daily life of Ho Chi Minh City.
                    """.replace("\n", " ").trim();
            case "Nhà thờ Chợ Quán" -> """
                    Welcome to Cho Quan Church, one of the oldest Catholic churches in Ho Chi Minh City. It carries important historical, architectural, and cultural traces of the Saigon and Cho Lon area.
                    The church is located at 120 Tran Binh Trong Street in the former District 5. According to parish history, the Cho Quan Catholic community was formed around 1722. After many historical changes, the present church was built in the late nineteenth century and features a distinctive Gothic style.
                    From outside, notice the tall bell tower, pointed arches, long windows, and elegant architectural lines. Inside, the high ceiling, columns, stained-glass windows, and sanctuary create a solemn and peaceful space.
                    Cho Quan Church remains a place of worship for generations of parishioners. For visitors, it offers an opportunity to learn about religious communities, the meeting of Eastern and Western architecture, and the cultural history of Saigon.
                    Please keep quiet, dress respectfully, and avoid disturbing religious activities. Thank you for discovering Cho Quan Church with VoiceMap.
                    """.replace("\n", " ").trim();
            default -> null;
        };
    }

    public static String localizedNarrationFor(String name, String language) {
        if ("vi".equals(language)) {
            return null;
        }
        if ("en".equals(language)) {
            return englishNarrationFor(name);
        }

        String localizedName = switch (language) {
            case "fr" -> switch (name) {
                case "Đại học Sài Gòn" -> "l’Université de Saïgon";
                case "Công viên 23 tháng 9" -> "le parc du 23 septembre";
                case "Chợ An Đông" -> "le marché An Đông";
                case "Nhà thờ Chợ Quán" -> "l’église de Chợ Quán";
                default -> name;
            };
            case "de" -> switch (name) {
                case "Đại học Sài Gòn" -> "die Universität Saigon";
                case "Công viên 23 tháng 9" -> "den Park des 23. September";
                case "Chợ An Đông" -> "den An-Dong-Markt";
                case "Nhà thờ Chợ Quán" -> "die Cho-Quan-Kirche";
                default -> name;
            };
            case "es" -> switch (name) {
                case "Đại học Sài Gòn" -> "la Universidad de Saigón";
                case "Công viên 23 tháng 9" -> "el Parque 23 de Septiembre";
                case "Chợ An Đông" -> "el Mercado An Dong";
                case "Nhà thờ Chợ Quán" -> "la Iglesia de Chợ Quán";
                default -> name;
            };
            case "it" -> switch (name) {
                case "Đại học Sài Gòn" -> "l’Università di Saigon";
                case "Công viên 23 tháng 9" -> "il Parco 23 Settembre";
                case "Chợ An Đông" -> "il Mercato An Dong";
                case "Nhà thờ Chợ Quán" -> "la Chiesa di Chợ Quán";
                default -> name;
            };
            case "ja" -> switch (name) {
                case "Đại học Sài Gòn" -> "サイゴン大学";
                case "Công viên 23 tháng 9" -> "9月23日公園";
                case "Chợ An Đông" -> "アン東市場";
                case "Nhà thờ Chợ Quán" -> "チョークアン教会";
                default -> name;
            };
            case "ko" -> switch (name) {
                case "Đại học Sài Gòn" -> "사이공 대학교";
                case "Công viên 23 tháng 9" -> "9월 23일 공원";
                case "Chợ An Đông" -> "안동 시장";
                case "Nhà thờ Chợ Quán" -> "쩌관 성당";
                default -> name;
            };
            case "zh-CN" -> switch (name) {
                case "Đại học Sài Gòn" -> "西贡大学";
                case "Công viên 23 tháng 9" -> "九月二十三日公园";
                case "Chợ An Đông" -> "安东市场";
                case "Nhà thờ Chợ Quán" -> "堤岸教堂";
                default -> name;
            };
            case "zh-TW" -> switch (name) {
                case "Đại học Sài Gòn" -> "西貢大學";
                case "Công viên 23 tháng 9" -> "九月二十三日公園";
                case "Chợ An Đông" -> "安東市場";
                case "Nhà thờ Chợ Quán" -> "堤岸教堂";
                default -> name;
            };
            case "th" -> switch (name) {
                case "Đại học Sài Gòn" -> "มหาวิทยาลัยไซ่ง่อน";
                case "Công viên 23 tháng 9" -> "สวน 23 กันยายน";
                case "Chợ An Đông" -> "ตลาดอันดง";
                case "Nhà thờ Chợ Quán" -> "โบสถ์โจวกวาน";
                default -> name;
            };
            case "ru" -> switch (name) {
                case "Đại học Sài Gòn" -> "Сайгонский университет";
                case "Công viên 23 tháng 9" -> "парк 23 сентября";
                case "Chợ An Đông" -> "рынок Ан Донг";
                case "Nhà thờ Chợ Quán" -> "церковь Чо Куан";
                default -> name;
            };
            case "id" -> switch (name) {
                case "Đại học Sài Gòn" -> "Universitas Saigon";
                case "Công viên 23 tháng 9" -> "Taman 23 September";
                case "Chợ An Đông" -> "Pasar An Dong";
                case "Nhà thờ Chợ Quán" -> "Gereja Cho Quan";
                default -> name;
            };
            case "pt" -> switch (name) {
                case "Đại học Sài Gòn" -> "Universidade de Saigon";
                case "Công viên 23 tháng 9" -> "Parque 23 de Setembro";
                case "Chợ An Đông" -> "Mercado An Dong";
                case "Nhà thờ Chợ Quán" -> "Igreja Cho Quan";
                default -> name;
            };
            case "ar" -> switch (name) {
                case "Đại học Sài Gòn" -> "جامعة سايغون";
                case "Công viên 23 tháng 9" -> "حديقة الثالث والعشرين من سبتمبر";
                case "Chợ An Đông" -> "سوق آن دونغ";
                case "Nhà thờ Chợ Quán" -> "كنيسة تشو كوان";
                default -> name;
            };
            case "ms" -> switch (name) {
                case "Đại học Sài Gòn" -> "Universiti Saigon";
                case "Công viên 23 tháng 9" -> "Taman 23 September";
                case "Chợ An Đông" -> "Pasar An Dong";
                case "Nhà thờ Chợ Quán" -> "Gereja Cho Quan";
                default -> name;
            };
            case "hi" -> switch (name) {
                case "Đại học Sài Gòn" -> "साइगॉन विश्वविद्यालय";
                case "Công viên 23 tháng 9" -> "23 सितंबर पार्क";
                case "Chợ An Đông" -> "आन डोंग बाजार";
                case "Nhà thờ Chợ Quán" -> "चो कुआन चर्च";
                default -> name;
            };
            default -> null;
        };
        if (localizedName == null) {
            return null;
        }

        return switch (language) {
            case "fr" -> "Bienvenue à " + localizedName + ", à Hô Chi Minh-Ville. Ce lieu fait partie de l’itinéraire culturel proposé par VoiceMap. Observez son histoire, son architecture et la vie quotidienne du quartier. Vous pouvez mettre la narration en pause, la reprendre et suivre les autres étapes sur la carte. Merci de découvrir ce lieu avec VoiceMap.";
            case "de" -> "Willkommen bei " + localizedName + " in Ho-Chi-Minh-Stadt. Dieser Ort ist Teil der von VoiceMap vorgeschlagenen Kulturroute. Beobachten Sie seine Geschichte, seine Architektur und das Alltagsleben des Viertels. Sie können die Führung pausieren, fortsetzen und die nächsten Stationen auf der Karte verfolgen. Vielen Dank, dass Sie diesen Ort mit VoiceMap entdecken.";
            case "es" -> "Bienvenido a " + localizedName + " en Ciudad Ho Chi Minh. Este lugar forma parte de la ruta cultural propuesta por VoiceMap. Observe su historia, su arquitectura y la vida cotidiana del barrio. Puede pausar y reanudar la narración y seguir las siguientes paradas en el mapa. Gracias por descubrir este lugar con VoiceMap.";
            case "it" -> "Benvenuti a " + localizedName + " nella città di Ho Chi Minh. Questo luogo fa parte dell’itinerario culturale proposto da VoiceMap. Osservate la sua storia, l’architettura e la vita quotidiana del quartiere. Potete mettere in pausa e riprendere la narrazione e seguire le tappe successive sulla mappa. Grazie per averlo scoperto con VoiceMap.";
            case "ja" -> localizedName + "へようこそ。ホーチミン市にあるこの場所は、VoiceMapがおすすめする文化散策ルートの一部です。歴史や建築、周辺の人々の暮らしに注目してください。音声は一時停止と再開ができ、地図で次のスポットも確認できます。VoiceMapと一緒に訪れていただき、ありがとうございます。";
            case "ko" -> localizedName + "에 오신 것을 환영합니다. 호찌민시에 있는 이 장소는 VoiceMap이 추천하는 문화 탐방 경로의 일부입니다. 이곳의 역사와 건축, 주변의 일상을 살펴보세요. 음성 안내는 일시 정지하고 다시 재생할 수 있으며 지도에서 다음 장소도 확인할 수 있습니다. VoiceMap과 함께해 주셔서 감사합니다.";
            case "zh-CN" -> "欢迎来到" + localizedName + "。这里位于胡志明市，是 VoiceMap 推荐文化路线的一站。请留意这里的历史、建筑和社区日常生活。您可以暂停或继续语音，并在地图上查看后续景点。感谢您与 VoiceMap 一起探索这里。";
            case "zh-TW" -> "歡迎來到" + localizedName + "。這裡位於胡志明市，是 VoiceMap 推薦文化路線的一站。請留意這裡的歷史、建築和社區日常生活。您可以暫停或繼續語音，並在地圖上查看後續景點。感謝您與 VoiceMap 一起探索這裡。";
            case "th" -> "ยินดีต้อนรับสู่" + localizedName + " ในเมืองโฮจิมินห์ สถานที่แห่งนี้เป็นส่วนหนึ่งของเส้นทางวัฒนธรรมที่ VoiceMap แนะนำ โปรดสังเกตประวัติศาสตร์ สถาปัตยกรรม และชีวิตประจำวันของชุมชน คุณสามารถหยุดหรือเล่นคำบรรยายต่อ และดูจุดถัดไปบนแผนที่ได้ ขอบคุณที่สำรวจสถานที่แห่งนี้กับ VoiceMap";
            case "ru" -> "Добро пожаловать в " + localizedName + " в Хошимине. Это место входит в культурный маршрут, предложенный VoiceMap. Обратите внимание на его историю, архитектуру и повседневную жизнь района. Вы можете поставить рассказ на паузу, продолжить его и посмотреть следующие остановки на карте. Спасибо, что открываете это место вместе с VoiceMap.";
            case "id" -> "Selamat datang di " + localizedName + " di Kota Ho Chi Minh. Tempat ini merupakan bagian dari rute budaya yang disarankan VoiceMap. Perhatikan sejarah, arsitektur, dan kehidupan sehari-hari di sekitarnya. Anda dapat menjeda atau melanjutkan narasi dan melihat pemberhentian berikutnya di peta. Terima kasih telah menjelajah bersama VoiceMap.";
            case "pt" -> "Bem-vindo a " + localizedName + ", na cidade de Ho Chi Minh. Este local faz parte da rota cultural sugerida pela VoiceMap. Observe a sua história, arquitetura e a vida quotidiana do bairro. Pode pausar e retomar a narração e acompanhar os próximos pontos no mapa. Obrigado por explorar este local com a VoiceMap.";
            case "ar" -> "مرحباً بكم في " + localizedName + " في مدينة هو تشي منه. هذا المكان جزء من المسار الثقافي الذي يقترحه VoiceMap. تأملوا تاريخه وهندسته المعمارية وحياة الحي اليومية. يمكنكم إيقاف الشرح مؤقتاً ومتابعته ومشاهدة المحطات التالية على الخريطة. شكراً لاكتشاف هذا المكان مع VoiceMap.";
            case "ms" -> "Selamat datang ke " + localizedName + " di Bandar Ho Chi Minh. Tempat ini merupakan sebahagian daripada laluan budaya yang dicadangkan oleh VoiceMap. Perhatikan sejarah, seni bina dan kehidupan harian kawasan ini. Anda boleh menjeda atau menyambung penerangan dan melihat hentian seterusnya pada peta. Terima kasih kerana meneroka tempat ini bersama VoiceMap.";
            case "hi" -> "हो ची मिन्ह सिटी में " + localizedName + " में आपका स्वागत है। यह स्थान VoiceMap द्वारा सुझाए गए सांस्कृतिक मार्ग का हिस्सा है। इसके इतिहास, वास्तुकला और आसपास के दैनिक जीवन पर ध्यान दें। आप विवरण को रोककर फिर से शुरू कर सकते हैं और मानचित्र पर अगले पड़ाव देख सकते हैं। VoiceMap के साथ इस स्थान को देखने के लिए धन्यवाद।";
            default -> null;
        };
    }

    private static final double SAIGON_UNIVERSITY_LATITUDE = 10.7595;
    private static final double SAIGON_UNIVERSITY_LONGITUDE = 106.6822;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public List<Poi> findNearbyPois(double latitude, double longitude, double radiusMeters) {
        List<Poi> saigonUniversityPois = createSaigonUniversityFallback(latitude, longitude, radiusMeters);
        if (!saigonUniversityPois.isEmpty()) {
            return saigonUniversityPois;
        }
        String query = """
                [out:json][timeout:15];
                (
                  nwr(around:%s,%s,%s)["name"]["tourism"];
                  nwr(around:%s,%s,%s)["name"]["historic"];
                  nwr(around:%s,%s,%s)["name"]["amenity"~"arts_centre|museum|place_of_worship|theatre"];
                  nwr(around:%s,%s,%s)["name"]["leisure"~"park|garden"];
                );
                out center tags;
                """.formatted(radiusMeters, latitude, longitude, radiusMeters, latitude, longitude,
                radiusMeters, latitude, longitude, radiusMeters, latitude, longitude);
        String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
        IllegalStateException lastFailure = null;
        for (String endpoint : Arrays.asList(
                "https://overpass-api.de/api/interpreter",
                "https://overpass.kumi.systems/api/interpreter")) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(25))
                    .header("User-Agent", "VoiceMap/1.0")
                    .header("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString("data=" + encodedQuery))
                    .build();
            try {
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 != 2) {
                    lastFailure = new IllegalStateException("OpenStreetMap Overpass trả về HTTP "
                            + response.statusCode());
                    continue;
                }
                List<Poi> pois = parsePois(response.body());
                if (!pois.isEmpty()) {
                    return pois;
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Yêu cầu lấy POI từ OpenStreetMap bị gián đoạn.", exception);
            } catch (IOException exception) {
                lastFailure = new IllegalStateException(
                        "Không thể kết nối máy chủ OpenStreetMap Overpass.", exception);
            }
        }
        List<Poi> photonPois = findNearbyPoisWithPhoton(latitude, longitude, radiusMeters);
        if (!photonPois.isEmpty()) {
            return photonPois;
        }
        throw new IllegalStateException("Không thể lấy POI gần vị trí từ OpenStreetMap.", lastFailure);
    }

    public List<Poi> createSaigonUniversityFallback(double latitude, double longitude, double radiusMeters) {
        return List.of(
                fallbackPoi("Đại học Sài Gòn", "Cơ sở chính Đại học Sài Gòn",
                        10.7595, 106.6822),
                fallbackPoi("Công viên 23 tháng 9", "Công viên trung tâm gần khu vực Quận 1",
                        10.7687, 106.6928),
                fallbackPoi("Chợ An Đông", "Khu chợ truyền thống nổi tiếng của Thành phố Hồ Chí Minh",
                        10.7563, 106.6812),
                fallbackPoi("Nhà thờ Chợ Quán", "Một công trình tôn giáo lâu đời tại Thành phố Hồ Chí Minh",
                        10.7544, 106.6735)
        );
    }

    private Poi fallbackPoi(String name, String description, double latitude, double longitude) {
        return Poi.builder()
                .name(name)
                .descriptionSource(narrationFor(name, description))
                .latitude(BigDecimal.valueOf(latitude))
                .longitude(BigDecimal.valueOf(longitude))
                .radiusM(50)
                .sourceLanguageCode("vi")
                .status("PUBLISHED")
                .build();
    }

    public static String narrationFor(String name, String fallbackDescription) {
        return switch (name) {
            case "Đại học Sài Gòn" -> SAIGON_UNIVERSITY_NARRATION;
            case "Công viên 23 tháng 9" -> SEPTEMBER_23_PARK_NARRATION;
            case "Chợ An Đông" -> AN_DONG_MARKET_NARRATION;
            case "Nhà thờ Chợ Quán" -> CHO_QUAN_CHURCH_NARRATION;
            default -> fallbackDescription + ". Dữ liệu điểm demo khu vực Đại học Sài Gòn.";
        };
    }

    private List<Poi> findNearbyPoisWithPhoton(double latitude, double longitude, double radiusMeters) {
        String uri = "https://photon.komoot.io/api/?lat=" + latitude
                + "&lon=" + longitude
                + "&limit=30&lang=default";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "VoiceMap/1.0")
                .GET()
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                return List.of();
            }
            List<Poi> pois = new ArrayList<>();
            for (JsonNode feature : objectMapper.readTree(response.body()).path("features")) {
                JsonNode properties = feature.path("properties");
                String name = properties.path("name").asText("").trim();
                JsonNode coordinates = feature.path("geometry").path("coordinates");
                if (name.isBlank() || !coordinates.isArray() || coordinates.size() < 2) {
                    continue;
                }
                double itemLongitude = coordinates.get(0).asDouble(Double.NaN);
                double itemLatitude = coordinates.get(1).asDouble(Double.NaN);
                if (Double.isNaN(itemLatitude) || Double.isNaN(itemLongitude)
                        || calculateDistance(latitude, longitude, itemLatitude, itemLongitude) > radiusMeters) {
                    continue;
                }
                String type = properties.path("type").asText("place");
                pois.add(Poi.builder()
                        .name(name)
                        .descriptionSource(name + " là một địa điểm gần vị trí của bạn. "
                                + "Dữ liệu địa điểm được lấy từ OpenStreetMap. Loại: " + type + ".")
                        .latitude(BigDecimal.valueOf(itemLatitude))
                        .longitude(BigDecimal.valueOf(itemLongitude))
                        .radiusM(50)
                        .sourceLanguageCode("vi")
                        .status("PUBLISHED")
                        .build());
            }
            return pois;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return List.of();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 6371000 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private List<Poi> parsePois(String responseBody) {
        try {
            List<Poi> pois = new ArrayList<>();
            JsonNode elements = objectMapper.readTree(responseBody).path("elements");
            for (JsonNode element : elements) {
                JsonNode tags = element.path("tags");
                String name = tags.path("name").asText("").trim();
                double latitude = element.path("lat").asDouble(Double.NaN);
                double longitude = element.path("lon").asDouble(Double.NaN);
                if (name.isBlank() || Double.isNaN(latitude) || Double.isNaN(longitude)) {
                    latitude = element.path("center").path("lat").asDouble(Double.NaN);
                    longitude = element.path("center").path("lon").asDouble(Double.NaN);
                }
                if (name.isBlank() || Double.isNaN(latitude) || Double.isNaN(longitude)) {
                    continue;
                }
                String category = firstTag(tags, "tourism", "historic", "amenity", "leisure");
                pois.add(Poi.builder()
                        .name(name)
                        .descriptionSource(name + " là một địa điểm tham quan gần vị trí của bạn. "
                                + "Thông tin được lấy từ OpenStreetMap." + category)
                        .latitude(BigDecimal.valueOf(latitude))
                        .longitude(BigDecimal.valueOf(longitude))
                        .radiusM(50)
                        .sourceLanguageCode("vi")
                        .status("PUBLISHED")
                        .build());
            }
            return pois;
        } catch (IOException exception) {
            throw new IllegalStateException("Dữ liệu POI từ OpenStreetMap không hợp lệ.", exception);
        }
    }

    private String firstTag(JsonNode tags, String... keys) {
        for (String key : keys) {
            String value = tags.path(key).asText("").trim();
            if (!value.isBlank()) {
                return " Loại: " + value + ".";
            }
        }
        return "";
    }
}
