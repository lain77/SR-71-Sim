package fbw.assets;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBTTAlignedQuad;
import org.lwjgl.stb.STBTTBakedChar;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.stb.STBTruetype.*;

public class Text {

    private static final int BITMAP_WIDTH = 512;
    private static final int BITMAP_HEIGHT = 512;
    private static final int FONT_SIZE = 24;

    private STBTTBakedChar.Buffer charData;
    private int textureId;

    public void init() throws IOException {
        //ESSA MERDA NÃO FUNCIONA
        try (InputStream is = getClass().getClassLoader().getResourceAsStream("res/Fonts/OCR.TTF")) {
            if (is == null) {
                throw new IOException("Fonte não encontrada: res/Fonts/OCR.TTF");
            }

            byte[] fontBytes = is.readAllBytes();
            ByteBuffer fontBuffer = BufferUtils.createByteBuffer(fontBytes.length);
            fontBuffer.put(fontBytes);
            fontBuffer.flip();

            ByteBuffer bitmap = BufferUtils.createByteBuffer(BITMAP_WIDTH * BITMAP_HEIGHT);
            charData = STBTTBakedChar.malloc(96); // 96 caracteres: ASCII 32-126

            int ok = stbtt_BakeFontBitmap(fontBuffer, FONT_SIZE, bitmap, BITMAP_WIDTH, BITMAP_HEIGHT, 32, charData);
            if (ok <= 0) {
                throw new RuntimeException("Falha ao criar bitmap da fonte");
            }

            textureId = glGenTextures();
            glBindTexture(GL_TEXTURE_2D, textureId);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_ALPHA, BITMAP_WIDTH, BITMAP_HEIGHT, 0, GL_ALPHA, GL_UNSIGNED_BYTE, bitmap);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);

            System.out.println("Fonte carregada com sucesso!");
        }
    }

    public void renderText(String text, float x, float y) {
        glBindTexture(GL_TEXTURE_2D, textureId);
        glBegin(GL_QUADS);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            float[] xpos = { x };
            float[] ypos = { y };

            STBTTAlignedQuad quad = STBTTAlignedQuad.malloc(stack);

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);

                if (c < 32 || c > 126) continue;
                
                stbtt_GetBakedQuad(charData, BITMAP_WIDTH, BITMAP_HEIGHT, c - 32, xpos, ypos, quad, true);

                glTexCoord2f(quad.s0(), quad.t0()); glVertex2f(quad.x0(), quad.y0());
                glTexCoord2f(quad.s1(), quad.t0()); glVertex2f(quad.x1(), quad.y0());
                glTexCoord2f(quad.s1(), quad.t1()); glVertex2f(quad.x1(), quad.y1());
                glTexCoord2f(quad.s0(), quad.t1()); glVertex2f(quad.x0(), quad.y1());
            }
        }

        glEnd();
    }
}
