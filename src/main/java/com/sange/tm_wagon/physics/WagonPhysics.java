package com.sange.tm_wagon.physics;

import com.sange.tm_wagon.entity.WagonEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.ArrayList;
import java.util.List;

/** Vehicle-only support solver. Fixed mass, no cargo weights, joints or friction simulation. */
public final class WagonPhysics {
    public static final double FORWARD_SPEED=.18, REVERSE_SPEED=.045;
    public static final double TRACK=42.0/16, WHEELBASE=40.0/16;
    public static final float NORMAL_PITCH=(float)Math.toRadians(25), NORMAL_ROLL=(float)Math.toRadians(22);
    public static final Vec3[] WHEELS={new Vec3(-21.0/16,0,-20.0/16),new Vec3(21.0/16,0,-20.0/16),
        new Vec3(-21.0/16,0,20.0/16),new Vec3(21.0/16,0,20.0/16)};
    public static double radius(int wheel) { return (wheel<2?10.5:13.5)/16; }
    public record Ground(double height,boolean forbidden) { public boolean present() { return Double.isFinite(height); } }
    public record Support(double[] heights,int mask) {
        public boolean has(int wheel) { return (mask&(1<<wheel))!=0; }
        public int count() { return Integer.bitCount(mask); }
    }
    private double verticalSpeed;
    private float pitchVelocity,rollVelocity;
    private boolean falling;
    private int tipDirection,recoveringTicks;
    private double fallStartHeight;
    public boolean falling() { return falling; }
    public void save(net.minecraft.nbt.CompoundTag tag) {
        tag.putBoolean("Falling",falling);tag.putDouble("VerticalSpeed",verticalSpeed);tag.putFloat("PitchVelocity",pitchVelocity);
        tag.putFloat("RollVelocity",rollVelocity);tag.putInt("TipDirection",tipDirection);tag.putDouble("FallStartHeight",fallStartHeight);tag.putInt("Recovering",recoveringTicks);
    }
    public void load(net.minecraft.nbt.CompoundTag tag) {
        falling=tag.getBoolean("Falling");verticalSpeed=Mth.clamp(tag.getDouble("VerticalSpeed"),-1.8,0);
        pitchVelocity=Mth.clamp(tag.getFloat("PitchVelocity"),-.09F,.09F);rollVelocity=Mth.clamp(tag.getFloat("RollVelocity"),-.09F,.09F);
        tipDirection=Mth.clamp(tag.getInt("TipDirection"),-1,1);fallStartHeight=tag.getDouble("FallStartHeight");recoveringTicks=Mth.clamp(tag.getInt("Recovering"),0,20);
    }
    public void reset() { verticalSpeed=0;pitchVelocity=rollVelocity=0;falling=false;tipDirection=0;recoveringTicks=0; }

    /** Only nearby collision tops count. Distant canyon floors never provide support. */
    public static Ground ground(Level level,Vec3 point,double up,double down,double halfWidth) {
        double best=Double.NEGATIVE_INFINITY;boolean forbidden=false;
        int minX=Mth.floor(point.x-halfWidth),maxX=Mth.floor(point.x+halfWidth);
        int minZ=Mth.floor(point.z-halfWidth),maxZ=Mth.floor(point.z+halfWidth);
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=Mth.floor(point.y+up);y>=Mth.floor(point.y-down)-1;y--) {
            BlockPos pos=new BlockPos(x,y,z);
            if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);
            for(AABB b:state.getCollisionShape(level,pos).toAabbs()) {
                b=b.move(x,y,z);
                if(b.maxX<=point.x-halfWidth || b.minX>=point.x+halfWidth || b.maxZ<=point.z-halfWidth || b.minZ>=point.z+halfWidth)continue;
                if(b.maxY<point.y-down-1e-5 || b.minY>point.y+up+1e-5)continue;
                if(state.is(BlockTags.FENCES)||state.is(BlockTags.WALLS)) { if(b.maxY>point.y+.05)forbidden=true;continue; }
                if(b.maxY>point.y+up+1e-5)continue;
                best=Math.max(best,b.maxY);
            }
        }
        return new Ground(best,forbidden);
    }
    public static Support support(WagonEntity wagon,WagonPose pose) { return support(wagon,pose,0); }
    private static Support support(WagonEntity wagon,WagonPose pose,int approach) {
        double[] heights=new double[4];int mask=0;
        Vec3 f=pose.vector(new Vec3(0,0,-1));f=new Vec3(f.x,0,f.z).normalize();
        for(int i=0;i<4;i++) {
            Vec3 point=wagon.wheelCentre(i,pose).add(0,-radius(i),0);
            Ground centre=ground(wagon.level(),point,1.001,1.05,.10);
            double best=centre.present()&&!centre.forbidden?centre.height:Double.NEGATIVE_INFINITY;
            if(Double.isFinite(best))mask|=1<<i;
            // The low boarding steps reach farther forward than the front wheels.
            // A short virtual approach ramp raises the front before those steps hit a block.
            double ramp=radius(i)+(i<2&&approach>0?1.25:.1);
            for(int j=1;j<=6&&approach!=0;j++) {
                double offset=ramp*j/6*.95;
                Ground g=ground(wagon.level(),point.add(f.scale(offset*approach)),1.001,1.05,.10);
                if(g.present()&&!g.forbidden)best=Math.max(best,g.height+Math.sqrt(ramp*ramp-offset*offset)-ramp);
            }
            heights[i]=best;
        }
        return new Support(heights,mask);
    }
    private static double average(Support s,int a,int b,double fallback) {
        return s.has(a)&&s.has(b)?(s.heights[a]+s.heights[b])/2:s.has(a)?s.heights[a]:s.has(b)?s.heights[b]:fallback;
    }
    public void tick(WagonEntity wagon,int input,int steering,boolean powered) {
        WagonPose old=wagon.pose();Vec3[] oldCentres=new Vec3[4];
        for(int i=0;i<4;i++)oldCentres[i]=wagon.wheelCentre(i,old);
        float steer=Mth.lerp(.28F,wagon.steering(),steering*(float)Math.toRadians(25));
        wagon.setSteering(steer);
        double speed=powered&&!falling?(input>0?FORWARD_SPEED:input<0?-REVERSE_SPEED:0):0;
        float yaw=old.yaw()+(float)Math.toDegrees(speed*Math.tan(steer)/WHEELBASE);
        Vec3 direction=new WagonPose(old.position(),yaw,0,0).forward();
        Vec3 horizontal=direction.scale(speed);
        if(!falling&&!wagon.horsesCanAdvance(horizontal)) { horizontal=Vec3.ZERO;yaw=old.yaw(); }
        if(falling) {
            Vec3 momentum=wagon.getDeltaMovement();horizontal=new Vec3(momentum.x,0,momentum.z);
            if(tipDirection!=0)horizontal=horizontal.add(direction.scale(.018*tipDirection));
            if(horizontal.horizontalDistance()>.28)horizontal=horizontal.normalize().scale(.28);
        }
        WagonPose ahead=new WagonPose(old.position().add(horizontal),yaw,old.pitch(),old.roll());
        Support supports=support(wagon,ahead,speed>0?1:speed<0?-1:0);wagon.setSupportMask(supports.mask);
        if(recoveringTicks>0&&!falling)recoveringTicks--;
        if(!falling && recoveringTicks==0 && (supports.count()==0 || (!supports.has(0)&&!supports.has(1)) || (!supports.has(2)&&!supports.has(3)))) {
            falling=true;fallStartHeight=old.position().y;tipDirection=!supports.has(0)&&!supports.has(1)?1:!supports.has(2)&&!supports.has(3)?-1:0;
        }
        float pitch=old.pitch(),roll=old.roll();double dy;
        if(!falling) {
            double front=average(supports,0,1,old.position().y),rear=average(supports,2,3,old.position().y);
            double left=average(supports,0,2,old.position().y),right=average(supports,1,3,old.position().y);
            float targetPitch=Mth.clamp((float)Math.atan2(front-rear,WHEELBASE),-NORMAL_PITCH,NORMAL_PITCH);
            float targetRoll=Mth.clamp((float)Math.atan2(right-left,TRACK),-NORMAL_ROLL,NORMAL_ROLL);
            if(recoveringTicks>0&&supports.count()<3) { targetPitch=0;targetRoll=0; }
            pitch=Mth.lerp(.3F,pitch,targetPitch);roll=Mth.lerp(.3F,roll,targetRoll);
            WagonPose tilted=new WagonPose(old.position(),yaw,pitch,roll);
            double required=Double.NEGATIVE_INFINITY;
            for(int i=0;i<4;i++)if(supports.has(i))required=Math.max(required,supports.heights[i]-(wagon.wheelCentre(i,tilted).y-radius(i)-old.position().y));
            dy=Mth.clamp(required-old.position().y,-.16,.22);
            verticalSpeed=0;pitchVelocity=rollVelocity=0;
        } else {
            verticalSpeed=Math.max(-1.8,verticalSpeed-.08);
            if(recoveringTicks>0) {
                pitch=Mth.lerp(.2F,pitch,0);roll=Mth.lerp(.2F,roll,0);
            } else {
                if(tipDirection!=0)pitchVelocity=Mth.clamp(pitchVelocity-.012F*tipDirection,-.09F,.09F);
                if(!supports.has(0)&&!supports.has(2)&&supports.count()>0)rollVelocity-=.006F;
                if(!supports.has(1)&&!supports.has(3)&&supports.count()>0)rollVelocity+=.006F;
                pitch=Mth.clamp(pitch+pitchVelocity,-1.22F,1.22F);
                roll=Mth.clamp(roll+rollVelocity,-.85F,.85F);
            }
            dy=verticalSpeed;
        }
        Vec3 requested=new Vec3(horizontal.x,dy,horizontal.z);
        Vec3 before=wagon.position();
        boolean landedOnLowerGround=move(wagon,requested,yaw,pitch,roll);
        Vec3 actual=wagon.position().subtract(before);wagon.setDeltaMovement(actual);
        if(falling) {
            // A landing must have nearby contact, not merely a distant raycast hit.
            Support landed=support(wagon,wagon.pose());
            boolean verticalContact=dy<-.001 && actual.y>dy+.01;
            if(verticalContact&&landedOnLowerGround) {
                // A shaft or corner can touch first. Keep gravity and momentum until the wheels settle.
                recoveringTicks=20;pitchVelocity=rollVelocity=0;
            }
            if(recoveringTicks>0&&verticalContact&&landed.count()>=3&&Math.abs(wagon.pitch())<.15&&Math.abs(wagon.roll())<.15&&uprightClear(wagon)) {
                falling=false;tipDirection=0;verticalSpeed=0;recoveringTicks=20;
            }
        }
        for(int i=0;i<4;i++) {
            Vec3 travel=wagon.wheelCentre(i,wagon.pose()).subtract(oldCentres[i]);
            Vec3 tangent=wagon.wheelForward(i);
            // Positive travel is forward; X-axis rolling rotation has the opposite sign.
            if((wagon.supportMask()&(1<<i))!=0&&(actual.horizontalDistance()>1e-6||Math.abs(Mth.wrapDegrees(wagon.getYRot()-old.yaw()))>1e-5))wagon.addWheelAngle(i,(float)(-travel.dot(tangent)/radius(i)));
        }
        wagon.syncMotion();
    }
    private static boolean uprightClear(WagonEntity wagon) {
        var boxes=wagon.motionBoxesAt(new WagonPose(wagon.position(),wagon.getYRot(),0,0));
        AABB bounds=boxes.getFirst();for(AABB box:boxes)bounds=bounds.minmax(box);
        for(VoxelShape shape:wagon.level().getBlockCollisions(wagon,bounds))for(AABB block:shape.toAabbs())
            if(boxes.stream().anyMatch(box->box.deflate(1e-5).intersects(block)))return false;
        return true;
    }
    /** Swept translation with bounded rotation increments; decorative meshes are never queried. */
    private boolean move(WagonEntity wagon,Vec3 motion,float yaw,float pitch,float roll) {
        boolean landedOnLowerGround=false;
        Ground floorBelow=falling?ground(wagon.level(),wagon.position().add(motion.x,0,motion.z),.5,16,.10):new Ground(Double.NEGATIVE_INFINITY,false);
        double landingHeight=floorBelow.present()&&floorBelow.height<fallStartHeight-1.05?floorBelow.height:Double.NEGATIVE_INFINITY;
        WagonPose start=wagon.pose();
        int steps=Math.max(1,(int)Math.ceil(Math.max(motion.length()/.10,
            Math.max(Math.abs(pitch-start.pitch()),Math.abs(roll-start.roll()))/.035)));
        steps=Math.min(32,steps);
        Vec3 step=motion.scale(1.0/steps);
        for(int n=1;n<=steps;n++) {
            float t=(float)n/steps;
            WagonPose previous=wagon.pose();
            WagonPose rotated=new WagonPose(wagon.position(),Mth.rotLerp(t,start.yaw(),yaw),
                Mth.lerp(t,start.pitch(),pitch),Mth.lerp(t,start.roll(),roll));
            List<AABB> boxes=wagon.motionBoxesAt(rotated);
            AABB bounds=boxes.getFirst();for(AABB b:boxes)bounds=bounds.minmax(b);
            if(!wagon.level().getWorldBorder().isWithinBounds(bounds.expandTowards(step)))break;
            boolean loaded=true;
            for(int x=Mth.floor(bounds.minX)>>4;x<=Mth.floor(bounds.maxX)>>4;x++)for(int z=Mth.floor(bounds.minZ)>>4;z<=Mth.floor(bounds.maxZ)>>4;z++)
                loaded&=wagon.level().hasChunk(x,z);
            if(!loaded)break;
            List<VoxelShape> terrain=new ArrayList<>();wagon.level().getBlockCollisions(wagon,bounds.expandTowards(step).inflate(.03)).forEach(terrain::add);
            terrain.addAll(wagon.level().getEntityCollisions(wagon,bounds.expandTowards(step).inflate(.03)));
            List<AABB> oldBoxes=wagon.motionBoxesAt(previous);
            boolean blockedRotation=false;
            for(VoxelShape shape:terrain)for(AABB block:shape.toAabbs()) {
                boolean next=boxes.stream().anyMatch(b->b.deflate(1e-5).intersects(block));
                boolean prior=oldBoxes.stream().anyMatch(b->b.deflate(1e-5).intersects(block));
                if(next&&!prior) { blockedRotation=true;break; }
            }
            if(blockedRotation) { rotated=previous;boxes=oldBoxes; }
            double y=limit(Direction.Axis.Y,step.y,boxes,terrain);
            if(y>step.y+.0001&&step.y<0)for(VoxelShape shape:terrain) {
                if(shape.bounds().maxY>landingHeight+.05)continue;
                for(AABB box:boxes)if(shape.collide(Direction.Axis.Y,box.deflate(1e-5),step.y)>step.y+.0001)landedOnLowerGround=true;
            }
            if(y<0)for(int i=0;i<4;i++) {
                Vec3 centre=wagon.wheelCentre(i,rotated),oldCentre=wagon.wheelCentre(i,previous);
                Ground g=ground(wagon.level(),new Vec3(centre.x,oldCentre.y-radius(i),centre.z),.15,Math.abs(y)+.2,.10);
                if(g.present()&&!g.forbidden) {
                    double contact=g.height-(centre.y-radius(i));
                    if(contact>y+.0001&&g.height<=landingHeight+.05)landedOnLowerGround=true;
                    y=Math.max(y,contact);
                }
            }
            boxes=shift(boxes,0,y,0);
            double x=limit(Direction.Axis.X,step.x,boxes,terrain);boxes=shift(boxes,x,0,0);
            double z=limit(Direction.Axis.Z,step.z,boxes,terrain);
            wagon.applyPose(new WagonPose(wagon.position().add(x,y,z),rotated.yaw(),rotated.pitch(),rotated.roll()));
        }
        return landedOnLowerGround;
    }
    private static List<AABB> shift(List<AABB> boxes,double x,double y,double z) { return boxes.stream().map(b->b.move(x,y,z)).toList(); }
    private static double limit(Direction.Axis axis,double distance,List<AABB> boxes,List<VoxelShape> terrain) {
        for(AABB box:boxes)for(VoxelShape shape:terrain)distance=shape.collide(axis,box.deflate(1e-5),distance);
        return distance;
    }
}
