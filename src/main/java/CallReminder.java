import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;

/*
 * Install credentials in ~/.credentials/call-reminder:
 * 1. Go to https://console.developers.google.com/
 * 2. Select Calendar API > Credentials > Call Reminder 
 * 3. Download JSON
 * 4. Rename it to client_secret.json
 * 5. Move it to src/main/resources/
 * 
 * Run with
 *
 *    cat Client1.csv | gradle -q run
 *
 */
public class CallReminder {
  private static final String APPLICATION_NAME = "Call Reminder";
  private static final java.io.File DATA_STORE_DIR = new java.io.File(
      System.getProperty("user.home"), ".credentials/call-reminder");

  private static FileDataStoreFactory DATA_STORE_FACTORY;
  private static final JsonFactory JSON_FACTORY = JacksonFactory.getDefaultInstance();
  private static HttpTransport HTTP_TRANSPORT;
  private static final List<String> SCOPES = Arrays.asList(CalendarScopes.CALENDAR);

  static {
    try {
      HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport();
      DATA_STORE_FACTORY = new FileDataStoreFactory(DATA_STORE_DIR);
    } catch (Throwable t) {
      t.printStackTrace();
      System.exit(1);
    }
  }

  public static Credential authorize() throws IOException {
    InputStream in = CallReminder.class.getResourceAsStream("/client_secret.json");
    GoogleClientSecrets clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, new InputStreamReader(in));

    GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, clientSecrets, SCOPES)
        .setDataStoreFactory(DATA_STORE_FACTORY)
        .setAccessType("offline")
        .build();
    Credential credential = new AuthorizationCodeInstalledApp(flow, new LocalServerReceiver())
        .authorize("user");
    //System.out.println("Credentials saved to " + DATA_STORE_DIR.getAbsolutePath());
    return credential;
  }

  static com.google.api.services.calendar.Calendar getCalendarService() throws IOException {
    Credential credential = authorize();
    return new com.google.api.services.calendar.Calendar.Builder(
        HTTP_TRANSPORT, JSON_FACTORY, credential)
        .setApplicationName(APPLICATION_NAME)
        .build();
  }

  static List<Order> parseOrders(Scanner sc) {
    sc.nextLine(); // skip header
    List<Order> orders = new ArrayList<>();
    while (sc.hasNext()) {
      String line = sc.next().trim();
      String[] tokens = line.split(",");
      orders.add(new Order(LocalDate.parse(tokens[0].trim()), Integer.parseInt(tokens[1].trim())));
    }
    Comparator<Order> cmp = Comparator.comparing(o -> o.date);
    orders.sort(cmp.reversed());
    return orders;
  }

  public static void main(String[] args) throws IOException {
    Scanner sc = new Scanner(System.in);
    String customer = sc.nextLine();

    List<Order> orders = parseOrders(sc);

    int n = orders.size();
    Order latest = orders.get(0);
    Order oldest = orders.get(n - 1);

    long days = oldest.date.until(latest.date, ChronoUnit.DAYS);
    long capsules = 0L;
    for (int i = 1; i < n; i++) {
      capsules += orders.get(i).quantity;
    }

    double averageCapsulePerDay = ((double) capsules) / days;
    long daysLeft = (long) Math.floor(latest.quantity / averageCapsulePerDay);
    long daysBuffer = 2L;
    LocalDate callReminderDate = latest.date.plusDays(daysLeft - daysBuffer);
    System.out.println("Chiamare il: " + callReminderDate);
    System.out.println("Media capsule al giorno: " + averageCapsulePerDay);

    double averageDaysBetweenOrders = 0;
    for (int i = 0; i < n - 1; i++) {
      long daysBetweenOrders = orders.get(i + 1).date.until(orders.get(i).date, ChronoUnit.DAYS);
      System.out.println("Giorni tra ordini: " + daysBetweenOrders);
      averageDaysBetweenOrders += daysBetweenOrders;
    }
    averageDaysBetweenOrders /= n - 1;
    System.out.println("Media giorni tra ordini: " + averageDaysBetweenOrders);

    createCalendarEvent(customer, callReminderDate);
    System.out.println("Evento creato");
  }

  static void createCalendarEvent(String customer, LocalDate date) {
    try {
      EventDateTime eventDate = new EventDateTime()
          .setDate(DateTime.parseRfc3339(date.toString()));

      Event event = new Event()
          .setStart(eventDate)
          .setEnd(eventDate)
          .setSummary("Chiamare " + customer)
          .setDescription("Altri dati...");

      getCalendarService().events()
          .insert("primary", event)
          .execute();

    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
  }
}

class Order {
  LocalDate date;
  int quantity;

  Order(LocalDate date, int quantity) {
    this.date = date;
    this.quantity = quantity;
  }
}
