import com.fastcgi.FCGIInterface;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

class Main {
    static DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String RESULT_JSON = "{\n" +
            "    \"answer\": %b,\n" +
            "    \"executionTime\": \"%s\",\n" +
            "    \"serverTime\": \"%s\"\n" +
            "}";

    private static final String HTTP_RESPONSE =
            "HTTP/1.1 200 OK\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Content-Length: %d\r\n" +
                    "\r\n" +
                    "%s";

    private static final String HTTP_ERROR =
            "HTTP/1.1 400 Bad Request\r\n" +
                    "Content-Type: application/json; charset=UTF-8\r\n" +
                    "Content-Length: %d\r\n" +
                    "\r\n" +
                    "%s";

    private static final String ERROR_JSON = "{\n" +
            "    \"reason\": \"%s\"\n" +
            "}";

    public static void main(String[] args) throws IOException {
        var fcgiInterface = new FCGIInterface();
        while (fcgiInterface.FCGIaccept() >= 0) {
            long startTime = System.nanoTime();
            try {
                var contentLengthStr = System.getProperty("CONTENT_LENGTH", "0");
                int contentLength;
                try {
                    contentLength = Integer.parseInt(contentLengthStr);
                } catch (NumberFormatException e) {
                    contentLength = 0;
                }

                byte[] buffer = new byte[contentLength];
                FCGIInterface.request.inStream.read(buffer, 0, contentLength);
                String postData = new String(buffer, StandardCharsets.UTF_8);

                System.out.println("Post Data: " + postData);

                Map<String, String> params = new HashMap<>();
                if (postData != null && !postData.isEmpty()) {
                    String[] pairs = postData.split("&");
                    for (String pair : pairs) {
                        String[] keyValue = pair.split("=");
                        if (keyValue.length == 2) {
                            String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8.name());
                            String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8.name());
                            params.put(key, value);
                        }
                    }
                }

                System.out.println("Parsed params: " + params);

                double x, y, r;
                try {
                    x = Double.parseDouble(params.getOrDefault("x", "0"));
                    y = Double.parseDouble(params.getOrDefault("y", "0"));
                    r = Double.parseDouble(params.getOrDefault("r", "0"));
                } catch (NumberFormatException e) {
                    System.out.println("Invalid number format: " + e.getMessage());
                    var json = String.format(ERROR_JSON, "Invalid parameters");
                    var responseBody = json.trim();
                    var response = String.format(HTTP_ERROR, responseBody.getBytes(StandardCharsets.UTF_8).length, responseBody);
                    FCGIInterface.request.outStream.write(response.getBytes(StandardCharsets.UTF_8));
                    FCGIInterface.request.outStream.flush();
                    continue;
                }

                // Инвертируем y для учета разницы в системах координат
                y = -y;

                System.out.println("X: " + x + ", Y: " + y + ", R: " + r);

                boolean isHit = isInsideRectangle(x, y, r) || isInsideTriangle(x, y, r) || isInsideCircle(x, y, r);

                long executionTime = System.nanoTime() - startTime;
                String serverTime = LocalDateTime.now().format(formatter);

                String responseBody = String.format(RESULT_JSON, isHit, executionTime, serverTime);

                String response = String.format(HTTP_RESPONSE, responseBody.getBytes(StandardCharsets.UTF_8).length, responseBody);

                FCGIInterface.request.outStream.write(response.getBytes(StandardCharsets.UTF_8));
                FCGIInterface.request.outStream.flush();

            } catch (Exception ex) {
                ex.printStackTrace();

                String errorJson = String.format(ERROR_JSON, ex.getMessage());
                String errorResponse = String.format(HTTP_ERROR, errorJson.getBytes(StandardCharsets.UTF_8).length, errorJson);

                FCGIInterface.request.outStream.write(errorResponse.getBytes(StandardCharsets.UTF_8));
                FCGIInterface.request.outStream.flush();
            } finally {
                try {
                    if (FCGIInterface.request.inStream != null) {
                        FCGIInterface.request.inStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                try {
                    if (FCGIInterface.request.outStream != null) {
                        FCGIInterface.request.outStream.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    // Прямоугольник в правой нижней части
    private static boolean isInsideRectangle(double x, double y, double r) {
        return (x >= 0 && x <= r / 2) && (y >= -r && y <= 0);
    }

    // Треугольник в правой верхней части
    private static boolean isInsideTriangle(double x, double y, double r) {
        return (x >= 0 && x <= r / 2) && (y >= 0 && y <= r / 2) && (y <= (-2 * x + r));
    }

    // Четверть окружности в левой нижней части
    private static boolean isInsideCircle(double x, double y, double r) {
        return (x <= 0 && y <= 0) && (x * x + y * y <= (r / 2) * (r / 2));
    }
}
