import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Map;
import javax.imageio.ImageIO;

/** 署名済み文字列を余白付きPNGへ変換する発行者用ツール。 */
class XpQrImage {
    public static void main(String[] args) throws Exception {
        var matrix = new QRCodeWriter().encode(args[0], BarcodeFormat.QR_CODE, 1024, 1024,
            Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 4));
        var image = new BufferedImage(matrix.getWidth(), matrix.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < matrix.getHeight(); y++) {
            for (int x = 0; x < matrix.getWidth(); x++) image.setRGB(x, y, matrix.get(x, y) ? 0 : 0xffffff);
        }
        if (!ImageIO.write(image, "png", new File(args[1]))) throw new IllegalStateException("PNGを保存できませんでした");
    }
}
