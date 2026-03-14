package fbw.system;

import org.joml.Vector3f;

public class Mundo {

    private Vector3f tamanho;  // limites do mundo
    private FlyByWire aviao;

    public Mundo(float largura, float altura, float profundidade, FlyByWire aviaoExistente) {
        this.tamanho = new Vector3f(largura, altura, profundidade);
        this.aviao = aviaoExistente;
    }

    public void start() {
        aviao.start();
    }

    public void update(double dt) {
        limitarAviao();
    }

    private void limitarAviao() {
        Vector3f pos = aviao.getPosicao();

        if (pos.x < 0) pos.x = 0;
        if (pos.y < 0) pos.y = 0;
        if (pos.z < 0) pos.z = 0;

        if (pos.x > tamanho.x) pos.x = tamanho.x;
        if (pos.y > tamanho.y) pos.y = tamanho.y;
        if (pos.z > tamanho.z) pos.z = tamanho.z;
    }

    public FlyByWire getAviao() {
        return aviao;
    }

    public Vector3f getTamanho() {
        return tamanho;
    }
}
