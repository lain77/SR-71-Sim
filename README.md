![test](https://images.steamusercontent.com/ugc/957480453021152883/EBB15E83438A391803AD3B688660643B0C9E6B51/?imw=5000&imh=5000&ima=fit&impolicy=Letterbox&imcolor=%23000000&letterbox=false)

#  SR-71 Blackbird Simulator

Um simulador de voo de alta performance focado na icônica aeronave de reconhecimento **SR-71 Blackbird**. Desenvolvido em Java 22 utilizando a biblioteca **LWJGL** para renderização gráfica de baixo nível via OpenGL.

---

##  Sobre

Este simulador busca recriar a experiência de pilotar a aeronave mais rápida do mundo. O foco está na física de voo em altas altitudes e velocidades supersônicas (Mach 3+), onde o gerenciamento de sistemas e a estabilidade se tornam desafios críticos.

**Especificações Alvo (SR-71A):**
* **Velocidade Máxima:** Mach 3.32.
* **Teto de Voo:** 85.000 pés (aprox. 26.000 metros).
* **Tripulação:** Piloto e RSO (Oficial de Sistemas de Reconhecimento).

---

##  Tecnologias Utilizadas

* **Linguagem:** Java 22 (utilizando as últimas funcionalidades da JVM).
* **Gráficos & Janela:** [LWJGL 3](https://www.lwjgl.org/) (Lightweight Java Game Library) com bindings para OpenGL e GLFW.
* **Matemática:** JOML (Java OpenGL Math Library) para cálculos de vetores e matrizes.
* **IDE:** Eclipse IDE.

---

##  Funcionalidades (Em Desenvolvimento)

* **Motor de Renderização 3D:** Implementação de shaders personalizados (GLSL) para atmosfera e terreno.
* **Física Supersônica:** Modelo de voo que simula o comportamento da aeronave acima de Mach 1.
* **Cockpit Dinâmico:** Instrumentação básica de voo (velocidade, altitude, horizonte artificial).
* **Gerenciamento de Combustível:** Simulação de consumo real em diferentes regimes de potência.

---

##  Como Executar (Preview)

Como o projeto está em progresso, certifique-se de ter o **JDK 22** (Preciso atualizar eu sei) instalado em sua máquina.

1. Clone o repositório:
   ```bash
   git clone [https://github.com/seu-usuario/seu-repositorio.git](https://github.com/seu-usuario/seu-repositorio.git)](https://github.com/lain77/SR-71-Sim.git)
   ```
2. Importe o projeto
   
3. Certifique-se de que as bibliotecas nativas do LWJGL estão configuradas no Build Path.
   
4. Execute a classe principal: src/main/Main.java.

## Escrever markdown é um saco mas as IAs escrevem markdowns muito ruins então eu tenho que ficar modificando
