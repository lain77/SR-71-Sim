package fbw.system;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.*;
import static org.lwjgl.opengl.GL30.*;

public class Framebuffer {
    private final int fboId;
    private final int textureId;
    private final int rboId;
    private final int width, height;

    public Framebuffer(int width, int height) {
        this.width  = width;
        this.height = height;

        fboId = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fboId);

        // Textura de cor
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, textureId);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA16F, width, height,
                     0, GL_RGBA, GL_FLOAT, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0,
                               GL_TEXTURE_2D, textureId, 0);

        // Renderbuffer de depth
        rboId = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, rboId);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, width, height);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT,
                                  GL_RENDERBUFFER, rboId);

        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE)
            throw new RuntimeException("Framebuffer incompleto!");

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bind()   { glBindFramebuffer(GL_FRAMEBUFFER, fboId); glViewport(0, 0, width, height); }
    public void unbind() { glBindFramebuffer(GL_FRAMEBUFFER, 0);     glViewport(0, 0, width, height); }
    public int  getTexture() { return textureId; }

    public void cleanup() {
        glDeleteFramebuffers(fboId);
        glDeleteTextures(textureId);
        glDeleteRenderbuffers(rboId);
    }
}