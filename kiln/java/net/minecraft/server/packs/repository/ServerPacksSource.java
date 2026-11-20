package net.minecraft.server.packs.repository;

/** Kiln's stand-in for {@code ServerPacksSource}: the packs a server loads from. */
public class ServerPacksSource extends net.minecraft.server.packs.repository.BuiltInPackSource {
    public ServerPacksSource(net.minecraft.world.level.validation.DirectoryValidator p0) { super((net.minecraft.server.packs.PackType) null, (net.minecraft.server.packs.VanillaPackResources) null, (net.minecraft.resources.Identifier) null, p0); }
    /** The game's built-in pack (D-217): its resources are the game's prepared ones, which Kiln reads where asked. */
    public static net.minecraft.server.packs.VanillaPackResources createVanillaPackSource() { return net.minecraft.server.packs.VanillaPackResources.kiln$game(); }
    public static net.minecraft.server.packs.repository.PackRepository createVanillaTrustedRepository() { kiln.Missing.called("net.minecraft.server.packs.repository.ServerPacksSource.createVanillaTrustedRepository"); return new net.minecraft.server.packs.repository.PackRepository(); }
    public static net.minecraft.server.packs.repository.PackRepository createPackRepository(net.minecraft.world.level.storage.LevelStorageSource.LevelStorageAccess p0) { kiln.Missing.called("net.minecraft.server.packs.repository.ServerPacksSource.createPackRepository"); return new net.minecraft.server.packs.repository.PackRepository(); }
    protected ServerPacksSource() { super(); }
}
