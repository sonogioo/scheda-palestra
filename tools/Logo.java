import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.ConvolveOp;
import java.awt.image.Kernel;
import java.io.File;
import javax.imageio.ImageIO;

/**
 * Genera il logo "focus": scritta bianca in grassetto con scia di movimento orizzontale.
 *
 *   java tools/Logo.java <cartella res> <cartella docs>
 */
public class Logo {

    public static void main(String[] args) throws Exception {
        File res = new File(args[0]);
        File docs = new File(args[1]);
        Font base = Font.createFont(Font.TRUETYPE_FONT, new File("C:/Windows/Fonts/arialbd.ttf"));

        // Icona adattiva: primo piano trasparente 432x432 (xxxhdpi, 108dp); testo nella zona sicura.
        write(render(base, 432, 432, 0.56, null, 0.16f), new File(res, "drawable-xxxhdpi/ic_launcher_fg.png"));
        // Scritta per l'intestazione della Home (sfondo trasparente).
        write(render(base, 960, 400, 0.62, null, 0.07f), new File(res, "drawable-nodpi/focus_wordmark.png"));
        // Anteprima per il README, su nero.
        write(render(base, 1024, 1024, 0.62, Color.BLACK, 0.16f), new File(docs, "logo.png"));
    }

    /** widthFraction = quanto della larghezza occupa la parte nitida della scritta. */
    static BufferedImage render(Font base, int w, int h, double widthFraction, Color bg, float trailAlpha) {
        String text = "focus";
        // Trova la dimensione del font che fa occupare al testo la frazione voluta.
        BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D pg = probe.createGraphics();
        float size = 100f;
        pg.setFont(base.deriveFont(size));
        size = (float) (size * (w * widthFraction) / pg.getFontMetrics().stringWidth(text));
        pg.dispose();
        Font font = base.deriveFont(size);

        int pad = (int) (w * 0.25); // margine per la scia, poi ritagliato
        int W = w + 2 * pad, H = h;

        // 1) Scia: copie del testo spostate orizzontalmente, più forti vicino al centro.
        BufferedImage trail = layer(W, H);
        Graphics2D t = graphics(trail, font);
        FontMetrics fm = t.getFontMetrics();
        int tw = fm.stringWidth(text);
        int x0 = (W - tw) / 2;
        int y0 = (H - fm.getHeight()) / 2 + fm.getAscent();
        int reach = (int) (size * 0.55); // lunghezza della scia
        int steps = 28;
        for (int i = -steps; i <= steps; i++) {
            if (i == 0) continue;
            double k = Math.abs(i) / (double) steps;
            float alpha = (float) (trailAlpha * (1 - k) * (1 - k));
            // Scia un po' più lunga a sinistra della "f" e a destra della "s", come nel riferimento.
            int dx = (int) (i * reach / (double) steps * (i < 0 ? 1.15 : 1.0));
            t.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            t.drawString(text, x0 + dx, y0);
        }
        t.dispose();
        trail = blurH(trail, Math.max(3, (int) (size * 0.18)));

        // 2) Testo principale, appena sfocato in orizzontale per l'effetto movimento.
        BufferedImage sharp = layer(W, H);
        Graphics2D s = graphics(sharp, font);
        s.drawString(text, x0, y0);
        s.dispose();
        BufferedImage soft = blurH(sharp, Math.max(3, (int) (size * 0.06)));

        // 3) Composizione e ritaglio.
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D o = out.createGraphics();
        if (bg != null) {
            o.setColor(bg);
            o.fillRect(0, 0, w, h);
        }
        o.drawImage(trail, -pad, 0, null);
        o.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
        o.drawImage(soft, -pad, 0, null);
        o.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.85f));
        o.drawImage(sharp, -pad, 0, null);
        o.dispose();
        return out;
    }

    static BufferedImage layer(int w, int h) {
        return new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
    }

    static Graphics2D graphics(BufferedImage img, Font font) {
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setColor(Color.WHITE);
        g.setFont(font);
        return g;
    }

    /** Sfocatura orizzontale (box blur ripetuto ≈ gaussiana). */
    static BufferedImage blurH(BufferedImage src, int radius) {
        int n = radius * 2 + 1;
        float[] k = new float[n];
        java.util.Arrays.fill(k, 1f / n);
        ConvolveOp op = new ConvolveOp(new Kernel(n, 1, k), ConvolveOp.EDGE_NO_OP, null);
        BufferedImage a = src;
        for (int i = 0; i < 3; i++) {
            BufferedImage dst = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
            BufferedImage pre = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_ARGB_PRE);
            pre.getGraphics().drawImage(a, 0, 0, null);
            op.filter(pre, dst);
            a = dst;
        }
        BufferedImage out = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_ARGB);
        out.getGraphics().drawImage(a, 0, 0, null);
        return out;
    }

    static void write(BufferedImage img, File f) throws Exception {
        f.getParentFile().mkdirs();
        ImageIO.write(img, "png", f);
        System.out.println(f + " " + img.getWidth() + "x" + img.getHeight());
    }
}
