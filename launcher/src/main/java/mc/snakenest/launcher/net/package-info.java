/**
 * Low-level HTTP + JSON plumbing shared by every API client
 * ({@code auth}, {@code modpack}, {@code news}). Built on the JDK's own
 * {@code java.net.http.HttpClient} (no extra HTTP dependency) plus a single
 * shared {@link com.google.gson.Gson} instance.
 */
package mc.snakenest.launcher.net;
