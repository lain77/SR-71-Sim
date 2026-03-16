package fbw.assets;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.*;

public class Text {

    private static final int BITMAP_WIDTH  = 1024;
    private static final int BITMAP_HEIGHT = 1024;

    private final Map<Integer, FontSize> sizes = new HashMap<>();
    private byte[] fontBytes;
    private int currentSize = 24;
    private float scale = 1f;  // escala de resolução

    private static class FontSize {
        STBTTBakedChar.Buffer charData;
        int textureId;
        int size;
    }

    public void init() throws IOException {
        try (InputStream is = getClass().getClassLoader()
                .getResourceAsStream("res/Fonts/OCR.TTF")) {
            if (is == null) throw new IOException("Fonte nao encontrada");
            fontBytes = is.readAllBytes();
        }

        // Bake tamanhos base
        bakeSize(16);
        bakeSize(20);
        bakeSize(24);
        bakeSize(32);
        bakeSize(48);
        bakeSize(64);

        System.out.println("Fonte carregada: 6 tamanhos prontos");
    }

    // Chamado quando muda a resolução (F11)
    public void setScale(float scale) {
        this.scale = scale;
    }

    private void bakeSize(int fontSize) {
        if (sizes.containsKey(fontSize)) return;  // já existe

        ByteBuffer fontBuffer = BufferUtils.createByteBuffer(fontBytes.length);
        fontBuffer.put(fontBytes).flip();

        int bw = fontSize > 40 ? 2048 : BITMAP_WIDTH;
        int bh = fontSize > 40 ? 2048 : BITMAP_HEIGHT;

        ByteBuffer bitmap = BufferUtils.createByteBuffer(bw * bh);
        STBTTBakedChar.Buffer charData = STBTTBakedChar.malloc(96);

        int ok = stbtt_BakeFontBitmap(fontBuffer, fontSize, bitmap, bw, bh, 32, charData);
        if (ok <= 0) {
            System.err.println("Aviso: bake parcial para tamanho " + fontSize);
        }

        int texId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, texId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, bw, bh, 0,
                     GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

        FontSize fs = new FontSize();
        fs.charData  = charData;
        fs.textureId = texId;
        fs.size      = fontSize;
        sizes.put(fontSize, fs);
    }

    // Garante que o tamanho escalado existe
    private int getScaledSize(int baseSize) {
        int scaledSize = Math.round(baseSize * scale);
        // Arredonda pro múltiplo de 2 mais próximo pra evitar bakes demais
        scaledSize = Math.max(8, ((scaledSize + 1) / 2) * 2);
        if (!sizes.containsKey(scaledSize)) {
            bakeSize(scaledSize);
        }
        return scaledSize;
    }

    public void setSize(int size) {
        currentSize = size;
    }

    public void renderText(String text, float x, float y) {
        renderText(text, x, y, currentSize);
    }

    public void renderText(String text, float x, float y, int size) {
        // Aplica escala: texto é renderizado maior mas posicionado com glScalef
        // Precisamos compensar: renderizar na resolução nativa
        int actualSize = getScaledSize(size);
        FontSize fs = sizes.get(actualSize);
        if (fs == null) fs = sizes.get(24);

        int bw = fs.size > 40 ? 2048 : BITMAP_WIDTH;
        int bh = fs.size > 40 ? 2048 : BITMAP_HEIGHT;

        // Compensa o glScalef: coordenadas passadas são em 1280x720,
        // mas queremos renderizar em resolução nativa
        // Então escala a posição pra nativa e desenha sem stretching
        float invScale = 1f / scale;

        glPushMatrix();
        // Desfaz o glScalef pro texto
        glScalef(invScale, invScale, 1f);
        // Agora estamos em coordenadas nativas — posiciona escalado
        float nx = x * scale;
        float ny = y * scale;

        glBindTexture(GL_TEXTURE_2D, fs.textureId);
        glBegin(GL_QUADS);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            float[] xpos = {nx};
            float[] ypos = {ny};
            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < 32 || c > 126) continue;

                stbtt_GetBakedQuad(fs.charData, bw, bh,
                                   c - 32, xpos, ypos, quad, true);

                glTexCoord2f(quad.s0(), quad.t0()); glVertex2f(quad.x0(), quad.y0());
                glTexCoord2f(quad.s1(), quad.t0()); glVertex2f(quad.x1(), quad.y0());
                glTexCoord2f(quad.s1(), quad.t1()); glVertex2f(quad.x1(), quad.y1());
                glTexCoord2f(quad.s0(), quad.t1()); glVertex2f(quad.x0(), quad.y1());
            }
        }
        glEnd();
        glPopMatrix();
    }
}