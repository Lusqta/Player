# Script utilitário para Depuração por Wi-Fi (ADB Wireless Debugging)
param(
    [string]$Action = "menu",
    [string]$DeviceIp = "",
    [string]$Port = "",
    [string]$PairCode = ""
)

$apkPath = "$PSScriptRoot\app\build\outputs\apk\debug\app-debug.apk"

function Show-Header {
    Clear-Host
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "       DEPURAÇÃO POR WI-FI - ANDROID (LUCAS PLAYER)       " -ForegroundColor Yellow
    Write-Host "==========================================================" -ForegroundColor Cyan
    Write-Host "Rede local do PC: 192.168.18.x (Verifique se o celular está no mesmo Wi-Fi)`n" -ForegroundColor DarkGray
}

function Install-And-Launch {
    if (-not (Test-Path $apkPath)) {
        Write-Host "[-] APK nao encontrado em $apkPath. Compile com ./gradlew.bat assembleDebug primeiro." -ForegroundColor Red
        return
    }
    Write-Host "[*] Instalando app-debug.apk no dispositivo..." -ForegroundColor Cyan
    adb install -r $apkPath
    if ($LASTEXITCODE -eq 0) {
        Write-Host "[+] APK instalado com sucesso!" -ForegroundColor Green
        Write-Host "[*] Iniciando MainActivity..." -ForegroundColor Cyan
        adb shell am start -n com.lucas.player/.MainActivity
        Write-Host "[+] App iniciado!" -ForegroundColor Green
    } else {
        Write-Host "[-] Falha ao instalar o APK." -ForegroundColor Red
    }
}

function Follow-Logs {
    Write-Host "[*] Acompanhando logs (Ctrl+C para sair)..." -ForegroundColor Yellow
    adb logcat -v color -s AudioEngine:V PlaybackService:V MainActivity:V Oboe:V
}

if ($Action -eq "pair") {
    if (-not $DeviceIp -or -not $Port -or -not $PairCode) {
        Write-Host "Uso: .\debug-wifi.ps1 -Action pair -DeviceIp <ip> -Port <porta_pareamento> -PairCode <codigo>" -ForegroundColor Red
        exit 1
    }
    Write-Host "[*] Pareando com $DeviceIp`:$Port usando código $PairCode..." -ForegroundColor Cyan
    adb pair "$DeviceIp`:$Port" $PairCode
    exit 0
}

if ($Action -eq "connect") {
    if (-not $DeviceIp) {
        Write-Host "Uso: .\debug-wifi.ps1 -Action connect -DeviceIp <ip> [-Port <porta>]" -ForegroundColor Red
        exit 1
    }
    $target = if ($Port) { "$DeviceIp`:$Port" } else { "$DeviceIp`:5555" }
    Write-Host "[*] Conectando a $target..." -ForegroundColor Cyan
    adb connect $target
    adb devices
    exit 0
}

if ($Action -eq "install") {
    Install-And-Launch
    exit 0
}

if ($Action -eq "logcat") {
    Follow-Logs
    exit 0
}

# Modo Interativo (Menu)
while ($true) {
    Show-Header
    Write-Host "Dispositivos ADB atualmente detectados:" -ForegroundColor Green
    adb devices
    Write-Host ""
    Write-Host "Escolha uma opcao:" -ForegroundColor White
    Write-Host "1) Parear novo aparelho (Android 11+ via código de 6 dígitos)"
    Write-Host "2) Conectar a aparelho já pareado (Android 11+ com IP e porta dinâmica)"
    Write-Host "3) Configurar modo Wi-Fi clássico via cabo USB (adb tcpip 5555 - Android 10 ou anterior)"
    Write-Host "4) Instalar app-debug.apk e abrir o app no celular"
    Write-Host "5) Ver logs em tempo real (Logcat filtrado do Player)"
    Write-Host "0) Sair"
    Write-Host ""

    $choice = Read-Host "Opcao"

    switch ($choice) {
        "1" {
            Write-Host "`nNo celular:" -ForegroundColor Yellow
            Write-Host "1. Abra 'Opções do Desenvolvedor' > 'Depuração sem fio' (ative-a)"
            Write-Host "2. Toque em 'Parear dispositivo com código de pareamento'"
            Write-Host "3. O Android exibirá: Endereço IP e porta (ex: 192.168.18.50:41235) e Código de pareamento (6 dígitos)`n"
            
            $inputIpPort = Read-Host "Digite o Endereço IP e porta exibidos para PAREAMENTO (ex: 192.168.18.50:41235)"
            $code = Read-Host "Digite o Código de pareamento de 6 dígitos"

            if ($inputIpPort -and $code) {
                Write-Host "`n[*] Pareando..." -ForegroundColor Cyan
                adb pair $inputIpPort $code
                
                Write-Host "`n[!] Agora observe a tela anterior de 'Depuração sem fio' no celular:" -ForegroundColor Yellow
                Write-Host "Ela mostra o 'Endereço IP e porta' PRINCIPAL de conexão (a porta geralmente muda após o pareamento)."
                $connectPort = Read-Host "Digite a porta principal de conexão (ou pressione Enter se for a mesma)"
                
                $ipOnly = $inputIpPort.Split(":")[0]
                $finalTarget = if ($connectPort) { "$ipOnly`:$connectPort" } else { $inputIpPort }
                
                Write-Host "[*] Conectando a $finalTarget..." -ForegroundColor Cyan
                adb connect $finalTarget
                adb devices
            }
            Pause
        }
        "2" {
            $target = Read-Host "`nDigite IP:Porta de 'Depuração sem fio' (ex: 192.168.18.50:39845)"
            if ($target) {
                adb connect $target
                adb devices
            }
            Pause
        }
        "3" {
            Write-Host "`n[1] Conecte o celular ao PC pelo cabo USB por 10 segundos com a Depuração USB ativada." -ForegroundColor Yellow
            Pause
            Write-Host "[*] Ativando porta 5555 via ADB..." -ForegroundColor Cyan
            adb tcpip 5555
            Write-Host "[+] Pronto! Pode desconectar o cabo USB agora." -ForegroundColor Green
            $ip = Read-Host "Digite o IP do celular na rede Wi-Fi (ex: 192.168.18.50)"
            if ($ip) {
                adb connect "$ip`:5555"
                adb devices
            }
            Pause
        }
        "4" {
            Install-And-Launch
            Pause
        }
        "5" {
            Follow-Logs
        }
        "0" {
            exit 0
        }
        default {
            Write-Host "Opcao invalida." -ForegroundColor Red
            Start-Sleep -Seconds 1
        }
    }
}
