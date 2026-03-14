![test](https://images.steamusercontent.com/ugc/957480453021152883/EBB15E83438A391803AD3B688660643B0C9E6B51/?imw=5000&imh=5000&ima=fit&impolicy=Letterbox&imcolor=%23000000&letterbox=false)

#  SR-71 Blackbird Simulator

A simulator/game (more like a game) about flying the SR-71 Blackbird. Sorry for the portuguese tests i have to modify this readme!
---

##  Sobre

The simulator focuses on flying the aircraft in reconnaissense missions above europe and soviet union. The focus is on the challenge of flying such a brutal aircraft and maintaning the systems while evading Surface to Air missiles.

**Specs (SR-71A):**
* **Max Speed:** Mach 3.5.
* **Max operational ceilling:** 85.000 feet (aprox. 26.000 meters).
* **Tripulation:** Pilot and RSO (Systems official).

---

##  Engines used

* **Language:** Java 22 (utilizando as últimas funcionalidades da JVM).
* **Graphics & Windows:** [LWJGL 3](https://www.lwjgl.org/) (Lightweight Java Game Library) com bindings para OpenGL e GLFW.
* **Mathmatics:** JOML (Java OpenGL Math Library) para cálculos de vetores e matrizes.
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
   git clone [(https://github.com/lain77/SR-71-Sim.git)](https://github.com/lain77/SR-71-Sim.git)
   ```
2. Importe o projeto
   
3. Certifique-se de que as bibliotecas nativas do LWJGL estão configuradas no Build Path.
   
4. Execute a classe principal: src/fbw.system/FlyData.

## Escrever markdown é um saco mas as IAs escrevem markdowns muito ruins então eu tenho que ficar modificando
