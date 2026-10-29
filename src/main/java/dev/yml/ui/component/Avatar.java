package dev.yml.ui.component;

import dev.yml.core.auth.Account;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelReader;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/** Player head rendered from the account's skin (falls back to a generated face). */
public final class Avatar extends StackPane {

    private static final Color[] HAIR = {
            Color.web("#3b2a1a"), Color.web("#4a3420"), Color.web("#2e2116"), Color.web("#5a4026")
    };
    private static final Color[] SKIN = {
            Color.web("#b98b63"), Color.web("#a97b55"), Color.web("#c4966f"), Color.web("#8f6446")
    };

    private final ImageView view = new ImageView();

    public Avatar(Account account, double size) {
        int hash = account == null || account.name == null ? 0 : Math.abs(account.name.hashCode());
        WritableImage image = headImage(account);
        if (image == null) {
            image = fallbackHead(hash);
        }
        view.setImage(image);
        view.setFitWidth(size);
        view.setFitHeight(size);
        view.setSmooth(false);
        view.setPreserveRatio(false);

        Rectangle clip = new Rectangle(size, size);
        clip.setArcWidth(size * 0.34);
        clip.setArcHeight(size * 0.34);
        setClip(clip);
        getChildren().add(view);
        setMinSize(size, size);
        setMaxSize(size, size);
        setPrefSize(size, size);
    }

    private static WritableImage headImage(Account account) {
        Image head = dev.yml.core.skin.SkinService.head(account);
        if (head == null) {
            return null;
        }
        WritableImage out = new WritableImage(8, 8);
        PixelReader reader = head.getPixelReader();
        if (reader == null) {
            return null;
        }
        PixelWriter writer = out.getPixelWriter();
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                writer.setArgb(x, y, reader.getArgb(x, y));
            }
        }
        return out;
    }

    private static WritableImage fallbackHead(int hash) {
        Color hair = HAIR[hash % HAIR.length];
        Color skin = SKIN[(hash / 7) % SKIN.length];
        WritableImage image = new WritableImage(8, 8);
        PixelWriter writer = image.getPixelWriter();
        Color eyeWhite = Color.web("#f4f4f4");
        Color iris = Color.web("#3d5a9e");
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                Color color = skin;
                if (y <= 1) {
                    color = hair;
                } else if (y == 2) {
                    color = (x == 0 || x == 7) ? hair : skin;
                } else if (y == 4 && (x == 1 || x == 2 || x == 5 || x == 6)) {
                    color = (x == 1 || x == 6) ? eyeWhite : iris;
                }
                writer.setColor(x, y, color);
            }
        }
        return image;
    }

    public void update(Account account) {
        Avatar fresh = new Avatar(account, view.getFitWidth());
        view.setImage(fresh.view.getImage());
    }
}
