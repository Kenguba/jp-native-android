package com.yuen.nativejpocr;

import android.graphics.Bitmap;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small OCR facade for Latin, Chinese and Japanese scripts.
 * Recognition models are provided by Google Play services to keep APK size low.
 */
final class MultilingualOcrRecognizer {
    interface Callback {
        void onSuccess(String text);
        void onFailure(Exception error);
    }

    private enum Script {
        LATIN,
        CHINESE,
        JAPANESE
    }

    private static final Pattern LOOKUP_TOKEN =
            Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}'’._-]{0,39}");

    private MultilingualOcrRecognizer() {}

    static void recognize(Bitmap bitmap, Callback callback) {
        InputImage image = InputImage.fromBitmap(bitmap, 0);

        TextRecognizer latin = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        TextRecognizer chinese = TextRecognition.getClient(
                new ChineseTextRecognizerOptions.Builder().build());
        TextRecognizer japanese = TextRecognition.getClient(
                new JapaneseTextRecognizerOptions.Builder().build());

        Task<Text> latinTask = latin.process(image);
        Task<Text> chineseTask = chinese.process(image);
        Task<Text> japaneseTask = japanese.process(image);

        Tasks.whenAllComplete(latinTask, chineseTask, japaneseTask)
                .addOnCompleteListener(ignored -> {
                    try {
                        String best = "";
                        int bestScore = Integer.MIN_VALUE;

                        if (latinTask.isSuccessful()) {
                            String value = textOf(latinTask);
                            int score = score(value, Script.LATIN);
                            if (score > bestScore) {
                                best = value;
                                bestScore = score;
                            }
                        }

                        if (chineseTask.isSuccessful()) {
                            String value = textOf(chineseTask);
                            int score = score(value, Script.CHINESE);
                            if (score > bestScore) {
                                best = value;
                                bestScore = score;
                            }
                        }

                        if (japaneseTask.isSuccessful()) {
                            String value = textOf(japaneseTask);
                            int score = score(value, Script.JAPANESE);
                            if (score > bestScore) {
                                best = value;
                                bestScore = score;
                            }
                        }

                        String token = firstLookupToken(best);
                        if (!token.isEmpty()) {
                            callback.onSuccess(token);
                            return;
                        }

                        Exception error = firstError(latinTask, chineseTask, japaneseTask);
                        callback.onFailure(error != null
                                ? error
                                : new IllegalStateException("没有识别到文字"));
                    } finally {
                        latin.close();
                        chinese.close();
                        japanese.close();
                    }
                });
    }

    private static String textOf(Task<Text> task) {
        Text result = task.getResult();
        return result == null ? "" : result.getText();
    }

    private static Exception firstError(Task<Text>... tasks) {
        for (Task<Text> task : tasks) {
            if (!task.isSuccessful() && task.getException() != null) {
                return task.getException();
            }
        }
        return null;
    }

    private static int score(String text, Script script) {
        if (text == null || text.trim().isEmpty()) return Integer.MIN_VALUE / 2;

        int score = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            if (isLatin(c)) {
                score += script == Script.LATIN ? 5 : 1;
            } else if (isKana(c)) {
                score += script == Script.JAPANESE ? 6 : 1;
            } else if (isHan(c)) {
                score += script == Script.CHINESE ? 5
                        : script == Script.JAPANESE ? 4
                        : 1;
            } else if (Character.isDigit(c)) {
                score += 2;
            }
        }
        return score;
    }

    private static boolean isLatin(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isKana(char c) {
        return (c >= '\u3040' && c <= '\u30ff') || c == 'ー';
    }

    private static boolean isHan(char c) {
        return (c >= '\u3400' && c <= '\u9fff') || c == '々' || c == '〆';
    }

    static String firstLookupToken(String raw) {
        if (raw == null) return "";

        String normalized = raw
                .replace('\r', ' ')
                .replace('\n', ' ')
                .replaceAll("\\s+", " ")
                .trim();

        Matcher matcher = LOOKUP_TOKEN.matcher(normalized);
        if (matcher.find()) return matcher.group();

        return normalized.length() <= 40
                ? normalized
                : normalized.substring(0, 40);
    }
}
