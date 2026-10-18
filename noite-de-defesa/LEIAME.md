# Noite de Defesa (mod Fabric para Minecraft 1.21.11)

Coloque uma **Bandeira de Defesa**. Toda noite uma horda vem te atazanar. Se você sobreviver
(matando todos ou chegando o amanhecer), você ganha **recompensas aleatórias** e a próxima noite
fica mais difícil. Não há limite de noites.

## Como jogar
- **Receita**: 3 lãs vermelhas + 2 gravetos (formato `RR / RS / _S`). Também está na aba Blocos Funcionais do criativo.
- Coloque a bandeira (só no Overworld; mínimo de 64 blocos entre duas bandeiras).
- A invasão começa quando anoitece e há um jogador a até 64 blocos da bandeira.
- Os monstros nascem a ~25 blocos, vão atrás de você e, sem alvo, caminham até a bandeira.
  **A bandeira não é destruída**: os monstros só te atazanam. Ao matar todos (ou chegar o amanhecer) a noite
  conta como vencida e você ganha recompensas aleatórias.
- Clique com o botão direito na bandeira para ver quantas noites você já defendeu.
- Quebrar a bandeira apaga o progresso dela (não há outro jeito de perder).

## Progressão (lenta e linear; tudo em `RaidComposer.java` e `RewardTable.java`)
- +0,5 monstro por noite (começa com 4, máx. 80), +1% de vida e +1% de dano por noite (tetos de 3x e 2,5x).
- Monstros liberados: zumbi (1), esqueleto (3), aranha (5), husk (8), creeper (12), stray (16), bruxa (20),
  aranha-da-caverna (24), vindicator (28), pillager (32).
- Espada nos zumbis: madeira (noite 3), pedra (15), ferro (30), diamante (50), netherite (80).
  Armadura: couro (10), malha (25), ferro (40), diamante (60), netherite (90).
- A cada 10 noites: **Capitão da Horda** (zumbi chefe) e recompensa extra. A cada 20: um Ravager.
- **Zumbi-Bomba** (segura TNT) a partir da **noite 30**: 1 por noite, +1 a cada 10 noites (máx. 8).
  Se ele bater de frente numa parede que não é terreno natural por ~1,5 s, explode (raio 3, crescendo até 5).
  A explosão segue a regra `mobGriefing` do mundo.

## Comandos
- `/defesa status`: lista as bandeiras do mundo.
- `/defesa iniciar`: força a invasão agora na bandeira mais próxima (teste).
- `/defesa noite <n>`: define quantas noites foram defendidas (teste).

Os dois últimos só funcionam em mundo singleplayer/LAN ou pelo console do servidor.

## Como obter o mod e usar no Prism Launcher

### 1. Gerar o `.jar` (uma vez só; escolha UM caminho)
- **Windows, mais simples:** instale o [JDK 21 (Temurin)](https://adoptium.net), descompacte este projeto e dê
  dois cliques em `COMPILAR-WINDOWS.bat`. Na primeira vez baixa tudo sozinho (precisa de internet).
  O mod aparece em `MOD-PRONTO\noite-de-defesa-1.0.0.jar`.
- **Sem instalar nada:** crie um repositório no GitHub, envie esta pasta inteira e abra a aba **Actions**.
  Ele compila sozinho; baixe o `.jar` em "Artifacts" (`noite-de-defesa-mod`).
- Linux/Mac: `./COMPILAR-LINUX-MAC.sh`.

### 2. Instalar no Prism
1. Crie uma instância **Minecraft 1.21.11** com o carregador **Fabric** (Loader 0.19.5 ou mais novo).
2. Abra a instância > **Editar** > **Mods**.
3. **Baixar mods** > procure **Fabric API** e instale (versão para 1.21.11).
4. **Adicionar arquivo** (ou arraste o `.jar`) e escolha `noite-de-defesa-1.0.0.jar`.
5. Jogue. Procure a "Bandeira de Defesa" no criativo (Blocos Funcionais) ou faça pela receita.

O progresso é salvo no arquivo `nightdefense_flags.json` dentro da pasta do mundo.

## Estrutura do código
- `NightDefenseMod`: inicialização e eventos.
- `ModBlocks` / `DefenseFlagBlock`: o bloco e o item.
- `DefenseManager`: bandeiras, ciclo da invasão, vida da bandeira, recompensas, salvamento.
- `RaidComposer`: quais monstros, quantos, força e equipamento por noite.
- `RewardTable`: tabela de recompensas.
- `DefenseCommands`: comandos.
