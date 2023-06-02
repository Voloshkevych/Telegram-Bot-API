package com.uni.lab;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import com.google.cloud.language.v1.*;
import com.google.cloud.translate.*;

import java.util.ArrayList;
import java.util.List;


public class MapsBot extends TelegramLongPollingBot {
    private static final String TELEGRAM_TOKEN = "your token";
    private static final String GOOGLE_MAPS_API_KEY = "your token";

    @Override
    public void onUpdateReceived(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String messageText = update.getMessage().getText();
            if (messageText.equals("/start")) {
                SendMessage message = new SendMessage();
                message.setChatId(update.getMessage().getChatId().toString());
                message.setText("Hello, I am a bot, you need to write the name of the city" +
                        " and I will show its map, coordinates, photo of a famous place " +
                        "and places nearby. I was created so that Derevianchenko " +
                        "Oleksandr Valeriyovich would close the exam to Voloshkevich Bogdan, " +
                        "and of course, to serve people");
                try {
                    execute(message);
                } catch (TelegramApiException e) {
                    e.printStackTrace();
                }
            } else {

                List<String> entities = extractEntities(messageText);

                for (String entity : entities) {
                    System.out.println("Entity: " + entity);
                }

                if (entities.size() > 0) {
                    String location = entitesToMap(entities);
                    System.out.println(location);

                    String[] details = getStaticMapUrl(location);
                    if (details != null) {
                        sendMapImage(update.getMessage().getChatId(), details[0]);
                        sendLocationCoordinates(update.getMessage().getChatId(), details[1]);

                        if (details[2] != null) {
                            String photoUrl = String.format(
                                    "https://maps.googleapis.com/maps/api/place/photo?maxwidth=400&photoreference=%s&key=%s",
                                    details[2], GOOGLE_MAPS_API_KEY);
                            sendPhoto(update.getMessage().getChatId(), photoUrl, "Famous Place Photo:");
                        }

                        for (int i = 3; i < details.length; i++) {
                            sendNearbyPlaces(update.getMessage().getChatId(), details[i]);
                        }
                    }
                } else {
                    SendMessage message = new SendMessage();
                    message.setChatId(update.getMessage().getChatId().toString());
                    message.setText("I'm sorry, but I couldn't find a recognizable location in your message. Please try again.");
                    try {
                        execute(message);
                    } catch (TelegramApiException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }



    private String[] getStaticMapUrl(String location) {
        OkHttpClient client = new OkHttpClient();
        String geocodeUrl = String.format(
                "https://maps.googleapis.com/maps/api/geocode/json?address=%s&key=%s",
                URLEncoder.encode(location, StandardCharsets.UTF_8), GOOGLE_MAPS_API_KEY);

        try (Response response = client.newCall(new Request.Builder().url(geocodeUrl).build()).execute()) {
            String jsonData = response.body().string();
            JSONObject jsonObject = new JSONObject(jsonData);

            JSONArray results = jsonObject.getJSONArray("results");
            if (results.length() > 0) {
                JSONObject result = results.getJSONObject(0);
                String formattedAddress = result.getString("formatted_address");
                JSONObject geometry = result.getJSONObject("geometry");
                JSONObject locationObj = geometry.getJSONObject("location");
                double lat = locationObj.getDouble("lat");
                double lng = locationObj.getDouble("lng");

                String[] placeTypes = {"restaurant", "bank", "supermarket"};
                String[] details = new String[placeTypes.length + 3]; // added +1 for the photo reference
                details[0] = String.format(
                        "https://maps.googleapis.com/maps/api/staticmap?center=%f,%f&zoom=14&size=400x400&markers=%f,%f&key=%s",
                        lat, lng, lat, lng, GOOGLE_MAPS_API_KEY);
                details[1] = String.format("%f,%f", lat, lng);
                details[2] = null; // This will hold the photo reference

                for (int i = 0; i < placeTypes.length; i++) {
                    String url = String.format(
                            "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=%f,%f&radius=1500&type=%s&key=%s",
                            lat, lng, placeTypes[i], GOOGLE_MAPS_API_KEY);

                    try (Response placeResponse = client.newCall(new Request.Builder().url(url).build()).execute()) {
                        jsonData = placeResponse.body().string();
                        jsonObject = new JSONObject(jsonData);
                        results = jsonObject.getJSONArray("results");

                        // If this is the first type (restaurant) and there are results, get the photo reference
                        if (i == 0 && results.length() > 0) {
                            JSONObject firstResult = results.getJSONObject(0);
                            if (firstResult.has("photos")) {
                                String photoReference = firstResult.getJSONArray("photos").getJSONObject(0).getString("photo_reference");
                                details[2] = photoReference;
                            }
                        }

                        StringBuilder sb = new StringBuilder();
                        sb.append(Character.toUpperCase(placeTypes[i].charAt(0)) + placeTypes[i].substring(1) + ":\n");

                        for (int j = 0; j < 5 && j < results.length(); j++) {
                            JSONObject placeResult = results.getJSONObject(j);
                            String name = placeResult.getString("name");
                            String address = placeResult.getString("vicinity");
                            sb.append(j + 1).append(". ").append(name).append(" - ").append(address).append("\n");
                        }
                        details[i + 3] = sb.toString(); // start from the fourth position
                    } catch (IOException | JSONException e) {
                        e.printStackTrace();
                    }
                }
                return details;
            }
        } catch (IOException | JSONException e) {
            e.printStackTrace();
        }
        return null;
    }


    private void sendMapImage(Long chatId, String url) {
        SendPhoto msg = new SendPhoto();
        msg.setChatId(String.valueOf(chatId));
        msg.setPhoto(new InputFile(url));

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendLocationCoordinates(Long chatId, String coordinates) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(coordinates);

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendNearbyPlaces(Long chatId, String places) {
        SendMessage msg = new SendMessage();
        msg.setChatId(String.valueOf(chatId));
        msg.setText(places);

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private void sendPhoto(Long chatId, String photoUrl, String caption) {
        InputFile photo = new InputFile(photoUrl);
        SendPhoto msg = new SendPhoto();
        msg.setChatId(String.valueOf(chatId));
        msg.setPhoto(photo);
        msg.setCaption(caption);

        try {
            execute(msg);
        } catch (TelegramApiException e) {
            e.printStackTrace();
        }
    }

    private List<String> extractEntities(String text) {
        try (LanguageServiceClient language = LanguageServiceClient.create()) {
            Document doc = Document.newBuilder().setContent(text).setType(Document.Type.PLAIN_TEXT).build();
            AnalyzeEntitiesResponse response = language.analyzeEntities(doc, EncodingType.UTF16);

            List<String> entities = new ArrayList<>();
            for (Entity entity : response.getEntitiesList()) {
                entities.add(entity.getName());
            }

            return entities;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }


    private String translateText(String text, String targetLanguage) {
        // Instantiates a client
        Translate translate = TranslateOptions.getDefaultInstance().getService();

        // Translates some text into target language
        Translation translation = translate.translate(
                text,
                Translate.TranslateOption.sourceLanguage("auto"),
                Translate.TranslateOption.targetLanguage(targetLanguage));

        return translation.getTranslatedText();
    }

    private static String entitesToMap(List<String> entities) {
        StringBuilder result = new StringBuilder();
        for (String entity: entities) {
            result.append(entity);
            result.append(", ");
        }
        return result.length() > 0 ? result.substring(0, result.length() - 2) : "";
    }


    @Override
    public String getBotUsername() {
        return "YOUR_BOT_NAME";
    }

    @Override
    public String getBotToken() {
        return TELEGRAM_TOKEN;
    }
}
