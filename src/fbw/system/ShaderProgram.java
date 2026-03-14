package fbw.system;

import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;

import static org.lwjgl.opengl.GL20.*;

public class ShaderProgram {
    private final int programId;

    public ShaderProgram(String vertexSource, String fragmentSource) {
        int vert = compileShader(vertexSource, GL_VERTEX_SHADER);
        int frag = compileShader(fragmentSource, GL_FRAGMENT_SHADER);

        programId = glCreateProgram();
        if (programId == 0) throw new RuntimeException("Could not create program");

        glAttachShader(programId, vert);
        glAttachShader(programId, frag);
        glLinkProgram(programId);

        int linkStatus = glGetProgrami(programId, GL_LINK_STATUS);
        if (linkStatus == GL_FALSE) {
            String log = glGetProgramInfoLog(programId);
            throw new RuntimeException("Program link error: " + log);
        }

        // shaders podem ser deletados após o link
        glDetachShader(programId, vert);
        glDetachShader(programId, frag);
        glDeleteShader(vert);
        glDeleteShader(frag);
    }

    private int compileShader(String src, int type) {
        int id = glCreateShader(type);
        glShaderSource(id, src);
        glCompileShader(id);

        int status = glGetShaderi(id, GL_COMPILE_STATUS);
        if (status == GL_FALSE) {
            String log = glGetShaderInfoLog(id);
            throw new RuntimeException((type==GL_VERTEX_SHADER?"Vertex":"Fragment")+" shader compile error: " + log);
        }
        return id;
    }

    public void bind() { glUseProgram(programId); }
    public void unbind() { glUseProgram(0); }

    public int getUniformLocation(String name) { 
        return glGetUniformLocation(programId, name); 
    }

    public void setUniformMatrix4f(String name, Matrix4f mat) {
        int loc = getUniformLocation(name);
        var buffer = BufferUtils.createFloatBuffer(16);
        mat.get(buffer);
        glUniformMatrix4fv(loc, false, buffer);
    }

    public void cleanup() {
        glUseProgram(0);
        if (programId != 0) glDeleteProgram(programId);
    }
    
    public void setUniform3f(String name, float x, float y, float z) {
        int loc = getUniformLocation(name);
        glUniform3f(loc, x, y, z);
    }
    
    public void setUniform1i(String name, int value) {
        int loc = getUniformLocation(name);
        glUniform1i(loc, value);
    }
}
