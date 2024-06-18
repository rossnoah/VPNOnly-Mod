package dev.noah.vpnonly.mixin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.CookieStore;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;

@Mixin(ConnectScreen.class)
public class ConnectScreenMixin {

    @Shadow private Text status;
    // Cache for VPN check results
    private static final HashMap<String, Boolean> vpnCheckCache = new HashMap<>();

    @Inject(method = "connect(Lnet/minecraft/client/gui/screen/Screen;Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;ZLnet/minecraft/client/network/CookieStorage;)V", at = @At("HEAD"), cancellable = true)
    private static void injectConnect(Screen screen, MinecraftClient client, ServerAddress address, ServerInfo info, boolean quickPlay, @Nullable CookieStorage cookieStorage, CallbackInfo ci) {


//        skip checks for localhost and 127.0.0.1
        if (address.getAddress().equals("localhost") || address.getAddress().equals("127.0.0.1")) {
            return;
        }

        int result = hasPassedCheck();

        if (result == 1) {
            ci.cancel();
            client.setScreen(new DisconnectedScreen(screen, Text.translatable("connect.failed"), Text.of("Your connection was blocked because you are not using a VPN.")));
        }
        if (result == 2) {
            ci.cancel();
            client.setScreen(new DisconnectedScreen(screen, Text.translatable("connect.failed"), Text.of("Your connection was blocked because the system failed to check if you are using a VPN.")));
        }
    }

    private static int hasPassedCheck() {
        String ip;
//        System.out.println("Checking public IP...");
        try {
            ip = getPublicIp();
//            System.out.println("Your public IP is: " + ip);
        } catch (Exception e) {
//            System.out.println("Failed to check public IP.");
            return 2;
        }

        if (isVpnIp(ip)) {
//            System.out.println("VPN detected.");
            return 0;
        }
//        System.out.println("No VPN detected.");
        return 1;
    }

    private static String getPublicIp() {
        String ip = "Unknown";
        try {
            URL url = new URL("https://api.ipify.org");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            ip = in.readLine();
            in.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return ip;
    }

    private static boolean isVpnIp(String ip) {
        // Check the cache first
        if (vpnCheckCache.containsKey(ip)) {
            return vpnCheckCache.get(ip);
        }

        boolean isVpn = false;
        try {
            URL url = new URL("http://ip-api.com/json/" + ip + "?fields=proxy,hosting");
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String response = in.readLine();
            in.close();

            JsonObject jsonObject = JsonParser.parseString(response).getAsJsonObject();

            //if no proxy or not hosting exists return true (request kinda failed?)
            if (!jsonObject.has("proxy") || !jsonObject.has("hosting")) {
                return false;
            }

            if (jsonObject.get("proxy").getAsBoolean()) {
                isVpn = true;
            }
            if (jsonObject.get("hosting").getAsBoolean()) {
                isVpn = true;
            }
        } catch (Exception e) {
            return false;
        }

        // Store the result in the cache
        vpnCheckCache.put(ip, isVpn);
        return isVpn;
    }
}
