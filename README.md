# Creative Squad 4 (CS4) — Fabric, Minecraft 26.2

Mod de **relíquias lendárias**: itens do próprio Minecraft com habilidades e encantamentos "roubados".
Base técnica copiada do mod da Irmandade (IMDD) 26.2.

## Build
- JDK 25 e internet (o Gradle baixa Minecraft/Fabric).
- `./gradlew build` → `build/libs/cs4-0.1.0.jar`
- Versões em `gradle.properties` (minecraft 26.2, Loader 0.19.5, Fabric API 0.160.0+26.2, Loom 1.17-SNAPSHOT). Confira em https://fabricmc.net/develop.
- **Instale o mod no servidor E nos clientes**: o Lampião (armadura/itens invisíveis) precisa do mod no cliente.

## /relics
Só operadores (nível 2; para liberar a todos, remova o `.requires(...)` em `RelicsCommand`). Abre a GUI de 18 slots
`CS» Relíquia`. Clique esquerdo **ou** direito dá uma cópia do item e o item continua na GUI.

## Indestrutível
Qualquer item com `unbreakable={}` jogado no chão não toma dano de nada (explosão, fogo, lava, cacto, raio...), não some por
tempo e, se cair no void, fica parado no fundo do mundo em vez de ser apagado. (`/kill` em item de admin ainda funciona.)

## Relíquias (ordem dos slots)
| # | Item | Habilidade |
|---|------|-----------|
| 1 | Dragão Lendário (cabeça de dragão, com modelo próprio `cs4:dragao` para mostrar o brilho de encantado) | Botão direito: bafo de dragão (bola + nuvem que causa dano). Cooldown 3s (extra, `DRAGON_COOLDOWN`). Não pode ser colocada nem equipada. |
| 2 | Pena Lendária | Na mão (principal/secundária), após 13 ticks: Levitação XV por 3s. (`FEATHER_LOOP=true` repete enquanto segurar.) |
| 3 | Escudo Lendário | Botão direito: rajada sônica do Warden (10 de dano, mesmo empurrão). Cooldown 10s. |
| 4 | Lampião Lendário | Na mão: armadura e itens das mãos somem (terceira pessoa; na primeira pessoa fica normal), 10 ticks depois o jogador some (Invisibilidade XI). Ao largar: armadura volta e, 10 ticks depois, o efeito acaba. Só elytra, cabeças e abóbora continuam aparecendo. |
| 5 | Batata do Bobo | No inventário: Lentidão V. |
| 6 | Relógio Lendário | Botão direito: GUI "Selecione um Jogador " com as cabeças dos online (9, 18, 27... slots). Clicar numa cabeça transforma você nele: skin, nome na cabeça, tab, report, sugestões do chat/`/tell`, nome no chat e mensagens. Clicar na própria cabeça desfaz. |
| 7 | Capacete Lendário dos Gêmeos | Equipado: Respiração Aquática II (4s em loop). Quem usa o capacete divide a mesma vida (dano e cura); se um morre, os outros morrem com a mesma morte. |
| 8 | Bota Lendária | Equipada, após 15 ticks: Velocidade V por 5s em loop. |
| 9 | Picareta Lendária | Só o item (Eficiência X). |
| 10 | Espada do Caçador | Na mão: Mau Presságio VI por 4s. |
| 11 | Bússola Lendária | Botão direito: GUI de jogadores; o ponteiro passa a apontar para o escolhido e toca o som de XP para todos por perto. |
| 12 | Poção Lendária | Botão direito: lança uma poção persistente (mesma cor) de Cegueira por 10s. Cooldown 5s. |
| 13 | Tridente Lendário | Ao acertar uma entidade (corpo a corpo ou arremessado): Fadiga de Mineração II por 10s. |

Os itens estão definidos com a sintaxe do `/give` em `RelicRegistry.DEFINITIONS` (uma linha por relíquia) — edite lá.

## Ajustes feitos nos seus `/give` (1.21.4 → 26.2)
- `enchantments={levels:{...}}` → `enchantments={...}`; `attribute_modifiers` agora é lista direta, nomes sem `generic.`;
  `show_in_tooltip:false` → `tooltip_display={hidden_components:[...]}`; `fire_resistant` → `damage_resistant`.
- Níveis 0 (`depth_strider:0`, `soul_speed:0`, `respiration:0`) removidos — nível 0 é inválido.
- O atributo `luck` com valor 0 foi tirado (não fazia nada).
- Poção e Tridente receberam `unbreakable`, como você pediu.
- Cor do "CS" na GUI: `#fbf0f` tem 5 dígitos; usei `#fbf00f`. Troque em `RelicsMenu`.

## ⚠ Não foi compilado
Foi escrito sem acesso à internet nem aos jars do jogo, usando os mesmos nomes de API do port 26.2 da Irmandade (que também
não tinha sido compilado). O primeiro `./gradlew build` pode apontar nomes a ajustar. Pontos mais incertos:
1. `ItemEntityMixin` (`ItemEntity.hurtServer` / `tick`) — se o nome não bater o jogo não abre (é o mixin do "indestrutível").
2. `Disguise`: usa reflexão em `ChunkMap$TrackedEntity.removePlayer/updatePlayer` para re-spawnar o jogador (nome/skin na cabeça).
   Se falhar, aparece um aviso no log e nome/skin só mudam na cabeça quando a entidade recarregar (tab/chat funcionam).
3. `ResolvableProfile` (cabeças da GUI), `DamageSource.getWeaponItem()` (tridente), `Entity.getDisplayName` (nome no chat).
4. Lampião: `AvatarRenderer.extractRenderState` (mixin com `require = 0`: se o nome não bater, só não esconde a armadura).
5. Poção Lendária usa `/summon lingering_potion` com SNBT; se o formato mudar, aparece erro no log.
