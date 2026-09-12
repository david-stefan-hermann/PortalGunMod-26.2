package com.example.portalgun.block;

import com.example.portalgun.PortalGunMod;
import com.example.portalgun.block.entity.PortalGunPedestalBlockEntity;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;

public class PortalGunPedestalBlock extends BaseEntityBlock {
   public static final MapCodec<PortalGunPedestalBlock> CODEC = simpleCodec(PortalGunPedestalBlock::new);
   public static final EnumProperty<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;
   private static final double[][] SHAPE_BOXES = new double[][]{
      {1.6, 0.0, 1.6, 14.4, 2.8, 14.4}, {6.65, 2.55, 6.06, 9.35, 14.9, 9.35}, {4.4, 14.85, 4.95, 11.6, 16.05, 11.05}, {5.75, 15.9, 6.9, 10.25, 16.45, 8.85}
   };
   private static final VoxelShape NORTH_SHAPE = createShape(Direction.NORTH);
   private static final VoxelShape EAST_SHAPE = createShape(Direction.EAST);
   private static final VoxelShape SOUTH_SHAPE = createShape(Direction.SOUTH);
   private static final VoxelShape WEST_SHAPE = createShape(Direction.WEST);

   public PortalGunPedestalBlock(Properties settings) {
      super(settings);
      this.registerDefaultState((BlockState)((BlockState)this.getStateDefinition().any()).setValue(FACING, Direction.NORTH));
   }

   protected MapCodec<? extends BaseEntityBlock> codec() {
      return CODEC;
   }

   public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
      return new PortalGunPedestalBlockEntity(pos, state);
   }

   protected RenderShape getRenderShape(BlockState state) {
      return RenderShape.MODEL;
   }

   public BlockState getStateForPlacement(BlockPlaceContext ctx) {
      return (BlockState)this.defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
   }

   protected void createBlockStateDefinition(Builder<Block, BlockState> builder) {
      builder.add(new Property[]{FACING});
   }

   protected VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
      return getShapeForFacing((Direction)state.getValue(FACING));
   }

   protected VoxelShape getCollisionShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
      return getShapeForFacing((Direction)state.getValue(FACING));
   }

   private static VoxelShape getShapeForFacing(Direction facing) {
      return switch (facing) {
         case EAST -> EAST_SHAPE;
         case SOUTH -> SOUTH_SHAPE;
         case WEST -> WEST_SHAPE;
         default -> NORTH_SHAPE;
      };
   }

   private static VoxelShape createShape(Direction facing) {
      VoxelShape shape = Shapes.empty();

      for (double[] box : SHAPE_BOXES) {
         shape = Shapes.or(shape, createCuboidShape(box, facing));
      }

      return shape.optimize();
   }

   private static VoxelShape createCuboidShape(double[] box, Direction facing) {
      double minX = box[0];
      double minY = box[1];
      double minZ = box[2];
      double maxX = box[3];
      double maxY = box[4];
      double maxZ = box[5];

      return switch (facing) {
         case EAST -> Block.box(16.0 - maxZ, minY, minX, 16.0 - minZ, maxY, maxX);
         case SOUTH -> Block.box(16.0 - maxX, minY, 16.0 - maxZ, 16.0 - minX, maxY, 16.0 - minZ);
         case WEST -> Block.box(minZ, minY, 16.0 - maxX, maxZ, maxY, 16.0 - minX);
         default -> Block.box(minX, minY, minZ, maxX, maxY, maxZ);
      };
   }

   protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
      if (!stack.is(PortalGunMod.PORTAL_GUN)) {
         return InteractionResult.TRY_WITH_EMPTY_HAND;
      } else if (world.isClientSide()) {
         return InteractionResult.SUCCESS;
      } else if (world.getBlockEntity(pos) instanceof PortalGunPedestalBlockEntity pedestal && pedestal.isEmpty()) {
         pedestal.setPortalGun(stack.copyWithCount(1));
         stack.consume(1, player);
         return InteractionResult.SUCCESS_SERVER;
      } else {
         return InteractionResult.FAIL;
      }
   }

   protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
      if (!player.getMainHandItem().isEmpty()) {
         return InteractionResult.PASS;
      }

      if (world.isClientSide()) {
         return InteractionResult.SUCCESS;
      }

      if (world.getBlockEntity(pos) instanceof PortalGunPedestalBlockEntity pedestal && !pedestal.isEmpty()) {
         ItemStack stack = pedestal.removePortalGun();
         if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
         }

         return InteractionResult.SUCCESS_SERVER;
      } else {
         return InteractionResult.PASS;
      }
   }

   protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel world, BlockPos pos, boolean moved) {
      if (!moved && world.getBlockEntity(pos) instanceof PortalGunPedestalBlockEntity pedestal) {
         ItemStack stack = pedestal.removePortalGunWithoutSync();
         if (!stack.isEmpty()) {
            Containers.dropItemStack(world, pos.getX(), pos.getY(), pos.getZ(), stack);
         }
      }

      super.affectNeighborsAfterRemoval(state, world, pos, moved);
   }
}
