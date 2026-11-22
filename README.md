# Creative Squad 4 (cs4)

Mod **Fabric** para **Minecraft 26.2** que adiciona reliquias lendarias feitas com itens do proprio Minecraft.

- Java 25 · Fabric Loader 0.19.1 · Fabric API 0.152.1+26.2
- Comando: `/relics` (somente OP) abre a GUI com as reliquias; clique para receber uma copia.
- Qualquer item com `unbreakable` e indestrutivel como entidade (explosao, cacto, fogo, lava, despawn).

## Reliquias
Espada do Cacador · Pena · Escudo (sonic boom) · Pocao (lingering de cegueira) · Tridente (fadiga) ·
Picareta · Capacete dos Gemeos (vida compartilhada) · Botas (speed) · Cabeca de Dragao (bafo) ·
Lampiao (invisibilidade) · Batata do Bobo (lentidao) · Relogio (disfarce de skin/nome) · Bussola (rastreia jogador)

## Compilar
```
gradle wrapper      # uma vez, gera o ./gradlew (opcional)
gradle build        # o jar fica em build/libs/
gradle runServer    # servidor de teste
```

## GitHub Actions
`.github/workflows/main.yml` compila a cada push e salva o `.jar` em *Actions > Artifacts*.
Para publicar um release com o jar: `git tag v1.0.0 && git push origin v1.0.0`.
