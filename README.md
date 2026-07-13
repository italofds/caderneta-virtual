# Caderneta Virtual — App Android nativo

Registro automático de trajetos do carro, disparado pela conexão Bluetooth do
veículo. Kotlin + Jetpack Compose (Material 3), Room, FusedLocationProvider e
Google Maps. `minSdk 26`, `targetSdk 34` — testado para dispositivos modernos
(ex.: Galaxy S24, Android 14).

## Como funciona

1. **Onboarding** — só aparece quando **nenhum** dispositivo está vinculado.
   Lista os dispositivos Bluetooth já pareados no celular; o usuário marca quais
   correspondem a veículos a monitorar. Nas próximas aberturas o app vai direto
   para a lista (a decisão vem do banco, não da 1ª emissão do fluxo, que é vazia).
2. **Início automático** — `BluetoothConnectionReceiver` (registrado no
   manifesto, funciona mesmo com o app fechado) escuta `ACL_CONNECTED`. Se o
   dispositivo for um veículo vinculado, sobe o `TripRecordingService`. Para
   contornar as restrições do Android 12+ (um serviço `location` não pode ser
   criado em background), o serviço entra em *foreground* primeiro como
   `connectedDevice` — permitido a partir de um evento de conexão — e só então
   adiciona o tipo `location`. Grava data/hora e o ponto de origem.
3. **Em background** — o serviço acumula a rota via GPS (FusedLocation), somando
   a distância e persistindo pontos no banco. Uma notificação persistente
   (imediata) mostra a quilometragem ao vivo.
4. **Fim automático** — em `ACL_DISCONNECTED` o serviço fecha o trajeto com o
   horário, o destino (geocodificado) e o total de km, e então se encerra.
   Trajetos com **menos de 0,1 km** são descartados (não viram registro).
5. **Lista** — todos os trajetos, filtráveis por veículo e, opcionalmente,
   agrupados por dia (início do 1º, fim do último e soma de km do dia).
6. **Detalhe** — mapa com a rota percorrida + linha do tempo origem/destino.
7. **Odômetro** — individual (informa início, o fim é calculado) ou em lote
   (informa o odômetro inicial de uma seleção e o app cascateia início/fim de
   cada trajeto pela distância).

## Pré-requisitos

- Android Studio Koala (2024.1) ou mais recente
- JDK 17
- Uma **chave da API do Google Maps** (Maps SDK for Android)

## Configuração

1. Abra a pasta `android-app/` no Android Studio.
2. Crie `local.properties` (copie de `local.properties.example`) e ajuste
   `sdk.dir` para o caminho do seu Android SDK.
3. Informe a chave do Maps de uma destas formas:
   - em `gradle.properties`: `MAPS_API_KEY=AIza...`, **ou**
   - em `local.properties`: `MAPS_API_KEY=AIza...`
   Ela é injetada no manifesto como `${MAPS_API_KEY}`.
4. Sincronize o Gradle e rode em um dispositivo físico (o Bluetooth do carro e
   o GPS não funcionam bem no emulador).

## Permissões solicitadas

- `BLUETOOTH_CONNECT` (Android 12+) — ler dispositivos pareados e eventos de
  conexão.
- `ACCESS_FINE_LOCATION` + `ACCESS_BACKGROUND_LOCATION` — rastrear o trajeto com
  o app em segundo plano. No Android 10+ o usuário precisa conceder "Permitir o
  tempo todo" nas configurações de localização do app.
- `POST_NOTIFICATIONS` (Android 13+) — notificação do serviço em foreground.
- `FOREGROUND_SERVICE_LOCATION` — exigida no Android 14 para o tipo `location`.

> Dica: para a gravação em background funcionar de forma confiável, oriente o
> usuário a conceder localização "o tempo todo" e a desativar otimizações de
> bateria para o app (Samsung: *Bateria → Sem restrições*).

## Estrutura

```
app/src/main/java/com/caderneta/virtual/
├─ CadernetaApp.kt                 Application + repositório compartilhado
├─ data/
│  ├─ TripRepository.kt            Fonte única sobre o Room
│  └─ db/                          Entidades, DAOs e AppDatabase
├─ service/
│  ├─ BluetoothConnectionReceiver  Dispara start/stop na conexão do veículo
│  └─ TripRecordingService.kt      Foreground service que grava o trajeto
├─ ui/
│  ├─ MainActivity.kt              Navegação + permissões + dispositivos pareados
│  ├─ MainViewModel.kt             Estado de lista/filtro/agrupamento/seleção
│  ├─ theme/Theme.kt               Material 3, paleta âmbar
│  └─ screens/                     Onboarding, Lista, Detalhe, Lote, Configurações
└─ util/Format.kt                  Formatação pt-BR de data/hora/km/odômetro
```

## Observações de implementação

- **Odômetro em lote** (`TripDao.applyBatchOdometer`) roda em uma transação Room:
  ordena a seleção por horário, aplica o odômetro inicial ao primeiro e soma a
  distância arredondada de cada trajeto para obter o início do próximo.
- **Odômetro fim** é sempre derivado (`odometerStart + round(distanceMeters/1000)`),
  nunca digitado — coerente com a regra "número inteiro" de km.
- **Retomada após kill**: ao iniciar, o serviço fecha qualquer trajeto órfão que
  tenha ficado aberto.
- O protótipo interativo aprovado está em `../Caderneta Virtual.dc.html`.

## Build via linha de comando

```bash
cd android-app
./gradlew assembleDebug      # gera app/build/outputs/apk/debug/app-debug.apk
```

> O wrapper do Gradle (`gradlew`/`gradlew.bat` e `gradle-wrapper.jar`) é gerado
> pelo Android Studio na primeira sincronização, ou com `gradle wrapper` se você
> tiver o Gradle instalado.
