# What the generator couldn't make (tools/kiln-stubs.py)

Generated: 1175 classes in kiln/java-gen, and what 72 hand-written stand-ins lacked, inside them. Made for: BetterThanMending-2.3.0-merged.jar, TechReborn-6.2.1.jar, armour-durability-1.11.26+mc26.3.jar, spongesucc-3.1.2-1.26.3.jar.

## Members the mods use that Kiln's hand-written stand-ins lack (4)
- `net.minecraft.world.entity.Entity.<init>` `(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V`
- `net.minecraft.world.entity.LivingEntity.<init>` `(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/level/Level;)V`
- `net.minecraft.world.item.context.BlockPlaceContext.<init>` `(Lnet/minecraft/world/item/context/UseOnContext;)V`
- `net.minecraft.world.item.context.UseOnContext.<init>` `(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/phys/BlockHitResult;)V`

## Hand-written members the mods' access wideners make public: check they are (2)
- `net.minecraft.world.level.block.LiquidBlock.fluid` `Lnet/minecraft/world/level/material/FlowingFluid;`
- `net.minecraft.world.level.block.state.BlockBehaviour.Properties.id` `Lnet/minecraft/resources/ResourceKey;`

## Classes nested in hand-written stand-ins (0)

## Classes the game's jars don't have (8)
- `com.mojang.authlib.GameProfile`
- `com.mojang.authlib.GameProfileRepository`
- `com.mojang.authlib.minecraft.SessionService`
- `com.mojang.authlib.services.ServicesKeySet`
- `com.mojang.authlib.services.response.NameAndId`
- `io.netty.channel.ChannelFutureListener`
- `io.netty.channel.SimpleChannelInboundHandler`
- `org.lwjgl.PointerBuffer`

## Dropped because they didn't compile (36)
- `net.minecraft.server.players.NameAndId.<init>` (Lcom/mojang/authlib/services/response/NameAndId;)V: constructor NameAndId(Object) is already defined in record NameAndId
- `net.minecraft.util.StringRepresentable.StringRepresentableCodec` : StringRepresentableCodec is not abstract and does not override abstract method <T>encode(S,DynamicOps<T>,T) in Encoder
- `net.minecraft.core.RegistryAccess.lookupOrThrow` (Lnet/minecraft/resources/ResourceKey;)Lnet/minecraft/core/Registry;: <E>lookupOrThrow(ResourceKey<? extends Registry<? extends E>>) in RegistryAccess clashes with <T>lookupOrThrow(ResourceKey<? extends Registry<? extends T>>) in Provider
- `net.minecraft.client.renderer.feature.ModelFeatureRenderer` : type argument Object is not within bounds of type-variable Submit
- `net.minecraft.world.item.Items.WOOL` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.DYED_TERRACOTTA` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.CARPET` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.STAINED_GLASS` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.STAINED_GLASS_PANE` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.DYED_SHULKER_BOX` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.GLAZED_TERRACOTTA` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.CONCRETE` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.CONCRETE_POWDER` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.DYE` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.BED` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.BANNER` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.item.Items.DYED_CANDLE` Lnet/minecraft/world/level/block/ColorCollection;: incompatible types: cannot infer type arguments for ColorCollection<>
- `net.minecraft.world.level.material.FlowingFluid.createFluidStateDefinition` (Lnet/minecraft/world/level/block/state/StateDefinition$Builder;)V: type argument FluidState is not within bounds of type-variable S
- `net.minecraft.world.entity.OwnableEntity.getOwnerReference` ()Lnet/minecraft/world/entity/EntityReference;: type argument LivingEntity is not within bounds of type-variable StoredEntityType
- `net.minecraft.client.renderer.entity.TntMinecartRenderer` : type argument Object is not within bounds of type-variable T
- `net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket` : type argument Object is not within bounds of type-variable T
- `net.minecraft.world.item.crafting.display.SlotDisplay.Composite` : Composite is not abstract and does not override abstract method <T>resolve(ContextMap,DisplayContentsFactory<T>) in SlotDisplay
- `net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay` : ItemStackSlotDisplay is not abstract and does not override abstract method <T>resolve(ContextMap,DisplayContentsFactory<T>) in SlotDisplay
- `net.minecraft.world.item.crafting.display.SlotDisplay.Empty` : Empty is not abstract and does not override abstract method <T>resolve(ContextMap,DisplayContentsFactory<T>) in SlotDisplay
- `net.minecraft.client.renderer.feature.phase.TranslucentFeatureRenderPhase` : type argument Object is not within bounds of type-variable Submit
- `net.minecraft.world.item.crafting.display.SlotDisplay.Composite.type` ()Lnet/minecraft/world/item/crafting/display/SlotDisplay$Type;: type argument Composite is not within bounds of type-variable T
- `net.minecraft.world.item.crafting.display.SlotDisplay.ItemStackSlotDisplay.type` ()Lnet/minecraft/world/item/crafting/display/SlotDisplay$Type;: type argument ItemStackSlotDisplay is not within bounds of type-variable T
- `net.minecraft.world.item.crafting.display.SlotDisplay.Empty.type` ()Lnet/minecraft/world/item/crafting/display/SlotDisplay$Type;: type argument Empty is not within bounds of type-variable T
- `net.fabricmc.fabric.impl.recipe.sync.SynchronizedRecipesImpl` : SynchronizedRecipesImpl is not abstract and does not override abstract method <I,T>getFirstMatch(RecipeType<T>,I,Level) in SynchronizedRecipes
- `net.minecraft.world.level.storage.TagValueOutput` : TagValueOutput is not abstract and does not override abstract method <T>list(String,Codec<T>) in ValueOutput
- `net.minecraft.client.renderer.SubmitNodeStorage` : SubmitNodeStorage is not abstract and does not override abstract method <S>submitCrumblingOverlay(Model<? super S>,S,PoseStack,RenderType,int,int,int,CrumblingOverlay) in OrderedSubmitNodeCollector
- `net.minecraft.client.renderer.SubmitNodeCollection` : SubmitNodeCollection is not abstract and does not override abstract method <S>submitCrumblingOverlay(Model<? super S>,S,PoseStack,RenderType,int,int,int,CrumblingOverlay) in OrderedSubmitNodeCollector
- `net.minecraft.world.level.block.entity.DispenserBlockEntity` : made bare: DispenserBlockEntity is not abstract and does not override abstract method createMenu(int,Inventory,Player) in MenuConstructor
- `net.minecraft.resources.RegistryOps` : RegistryOps is not abstract and does not override abstract method <U>convertTo(DynamicOps<U>,T#2) in DynamicOps
- `net.minecraft.client.multiplayer.ClientConfigurationPacketListenerImpl` : made bare: ClientConfigurationPacketListenerImpl is not abstract and does not override abstract method handleCustomPayload(ClientboundCustomPayloadPacket) in ClientCommonPacketListener
- `net.minecraft.world.level.storage.TagValueInput` : TagValueInput is not abstract and does not override abstract method <T>listOrEmpty(String,Codec<T>) in ValueInput
