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
    public static final double FORWARD_SPEED=.18*1.3, REVERSE_SPEED=.045*1.3;
    public static final double PUSH_SPEED=.025;
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
    private final WagonDrive drive=new WagonDrive();
    private double verticalSpeed;
    private float pitchVelocity,rollVelocity;
    private boolean falling;
    private int tipDirection,recoveringTicks;
    private double fallStartHeight;
    private boolean driverRecovery;
    private double stepLiftCeiling=Double.NEGATIVE_INFINITY;
    private final double[] contactHeights={Double.NaN,Double.NaN,Double.NaN,Double.NaN};
    private Vec3 contactPosition;
    public boolean falling() { return falling; }
    public void save(net.minecraft.nbt.CompoundTag tag) {
        tag.putDouble("DriveSpeed",drive.speed());
        tag.putBoolean("Falling",falling);tag.putDouble("VerticalSpeed",verticalSpeed);tag.putFloat("PitchVelocity",pitchVelocity);
        tag.putFloat("RollVelocity",rollVelocity);tag.putInt("TipDirection",tipDirection);tag.putDouble("FallStartHeight",fallStartHeight);tag.putInt("Recovering",recoveringTicks);
        for(int i=0;i<4;i++)if(Double.isFinite(contactHeights[i]))tag.putDouble("Contact"+i,contactHeights[i]);
    }
    public void load(net.minecraft.nbt.CompoundTag tag) {
        drive.load(tag.getDouble("DriveSpeed"));
        falling=tag.getBoolean("Falling");verticalSpeed=Mth.clamp(tag.getDouble("VerticalSpeed"),-1.8,0);
        pitchVelocity=Mth.clamp(tag.getFloat("PitchVelocity"),-.09F,.09F);rollVelocity=Mth.clamp(tag.getFloat("RollVelocity"),-.09F,.09F);
        tipDirection=Mth.clamp(tag.getInt("TipDirection"),-1,1);fallStartHeight=tag.getDouble("FallStartHeight");recoveringTicks=Mth.clamp(tag.getInt("Recovering"),0,20);
        for(int i=0;i<4;i++)contactHeights[i]=tag.contains("Contact"+i)?tag.getDouble("Contact"+i):Double.NaN;
        contactPosition=null;driverRecovery=false;
    }
    public void reset() { drive.reset();verticalSpeed=0;pitchVelocity=rollVelocity=0;falling=false;tipDirection=0;recoveringTicks=0;driverRecovery=false;java.util.Arrays.fill(contactHeights,Double.NaN);contactPosition=null; }

    /** Only nearby collision tops count. Distant canyon floors never provide support. */
    public static Ground ground(Level level,Vec3 point,double up,double down,double halfWidth) {
        double best=Double.NEGATIVE_INFINITY;boolean forbidden=false;
        int minX=Mth.floor(point.x-halfWidth),maxX=Mth.floor(point.x+halfWidth);
        int minZ=Mth.floor(point.z-halfWidth),maxZ=Mth.floor(point.z+halfWidth);
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)for(int y=Mth.floor(point.y+up);y>=Mth.floor(point.y-down)-1;y--) {
            pos.set(x,y,z);
            if(!level.hasChunkAt(pos))continue;
            var state=level.getBlockState(pos);
            for(AABB b:state.getCollisionShape(level,pos).toAabbs()) {
                if(b.maxX+x<=point.x-halfWidth || b.minX+x>=point.x+halfWidth || b.maxZ+z<=point.z-halfWidth || b.minZ+z>=point.z+halfWidth)continue;
                double top=b.maxY+y;
                if(top<point.y-down-1e-5 || b.minY+y>point.y+up+1e-5)continue;
                if(state.is(BlockTags.FENCES)||state.is(BlockTags.WALLS)) { if(top>point.y+.05)forbidden=true;continue; }
                if(top>point.y+up+1e-5)continue;
                best=Math.max(best,top);
            }
        }
        return new Ground(best,forbidden);
    }
    public static Support support(WagonEntity wagon,WagonPose pose) { return sampleSupport(wagon,pose,0,null); }
    private static Support sampleSupport(WagonEntity wagon,WagonPose pose,int approach,double[] contacts) {
        double[] heights=new double[4];int mask=0;
        Vec3 f=pose.vector(new Vec3(0,0,-1));f=new Vec3(f.x,0,f.z).normalize();
        for(int i=0;i<4;i++) {
            Vec3 point=wagon.wheelCentre(i,pose).add(0,-radius(i),0);
            // Preserve the last terrain contact independently of the capped body tilt.
            // Rear wheels may hang above an earlier stair while the front axle climbs.
            if(contacts!=null&&Double.isFinite(contacts[i]))point=new Vec3(point.x,contacts[i],point.z);
            Ground centre=ground(wagon.level(),point,1.001,1.05,.10);
            double best=centre.present()&&!centre.forbidden?centre.height:Double.NEGATIVE_INFINITY;
            if(Double.isFinite(best))mask|=1<<i;
            if(contacts!=null)contacts[i]=best;
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
    public void tick(WagonEntity wagon,int input,int steering,boolean powered,int pushing) {
        stepLiftCeiling=Double.NEGATIVE_INFINITY;
        tickVehicle(wagon,input,steering,powered,pushing);
    }
    private void tickVehicle(WagonEntity wagon,int input,int steering,boolean powered,int pushing) {
        WagonPose old=wagon.pose();Vec3[] oldCentres=new Vec3[4];
        for(int i=0;i<4;i++)oldCentres[i]=wagon.wheelCentre(i,old);
        boolean unstable=falling||Math.abs(old.pitch())>NORMAL_PITCH+.1||Math.abs(old.roll())>NORMAL_ROLL+.1;
        boolean manualPush=!powered&&pushing!=0;
        driverRecovery=unstable&&(manualPush||(input!=0&&touchingGround(wagon)));
        float steer=Mth.lerp(.28F,wagon.steering(),steering*(float)Math.toRadians(25));
        wagon.setSteering(steer);
        int occupied=wagon.cargo().occupiedSlots();
        // 'powered' already verifies both live horses; no extra entity lookup or packet is needed.
        boolean doubleTeam=powered&&wagon.horseCapacity()==2;
        double limit=input<0?WagonSpeed.reverse(wagon.cargoBody()):WagonSpeed.forward(wagon.cargoBody(),
            occupied,wagon.boostedDrive(),doubleTeam);
        // A stranded driver can still rock a tipped wagon after its horses detach.
        if(driverRecovery&&!powered)limit*=.35;
        double speed=drive.tick(powered||driverRecovery?input:0,limit,occupied,wagon.cargo().capacity(),doubleTeam);
        if(manualPush) { speed=PUSH_SPEED*pushing;drive.reset(); }
        float yaw=old.yaw()+(float)Math.toDegrees(speed*Math.tan(steer)/wagon.wheelbase());
        Vec3 direction=new WagonPose(old.position(),yaw,0,0).forward();
        Vec3 horizontal=direction.scale(speed);
        if(!driverRecovery&&!falling&&!wagon.horsesCanAdvance(horizontal)) { horizontal=Vec3.ZERO;yaw=old.yaw(); }
        if(falling) {
            Vec3 momentum=wagon.getDeltaMovement();horizontal=new Vec3(momentum.x,0,momentum.z);
            // Hand pressure remains effective when the chassis, rather than its wheels,
            // rests on terrain. Gravity and swept collisions still constrain recovery.
            if(manualPush||input!=0&&(powered||driverRecovery))horizontal=horizontal.lerp(direction.scale(speed),.4);
            else if(tipDirection!=0)horizontal=horizontal.add(direction.scale(.018*tipDirection));
            double fallLimit=WagonSpeed.maxForward()*(16.0/15);
            if(horizontal.horizontalDistance()>fallLimit)horizontal=horizontal.normalize().scale(fallLimit);
        }
        WagonPose ahead=new WagonPose(old.position().add(horizontal),yaw,old.pitch(),old.roll());
        if(contactPosition!=null&&contactPosition.distanceToSqr(old.position())>1)java.util.Arrays.fill(contactHeights,Double.NaN);
        Support supports=sampleSupport(wagon,ahead,speed>0?1:speed<0?-1:0,falling||unstable?null:contactHeights);wagon.setSupportMask(supports.mask);
        if(recoveringTicks>0&&!falling)recoveringTicks--;
        if(!falling && recoveringTicks==0 && (unstable || supports.count()==0 || (!supports.has(0)&&!supports.has(1)) || (!supports.has(2)&&!supports.has(3)))) {
            falling=true;fallStartHeight=old.position().y;tipDirection=!supports.has(0)&&!supports.has(1)?1:!supports.has(2)&&!supports.has(3)?-1:0;
        }
        float pitch=old.pitch(),roll=old.roll();double dy;
        if(!falling) {
            double front=average(supports,0,1,old.position().y),rear=average(supports,2,3,old.position().y);
            double left=average(supports,0,2,old.position().y),right=average(supports,1,3,old.position().y);
            float targetPitch=Mth.clamp((float)Math.atan2(front-rear,wagon.wheelbase()),-NORMAL_PITCH,NORMAL_PITCH);
            float targetRoll=Mth.clamp((float)Math.atan2(right-left,wagon.cargoBody().wheelHalfTrack()*2),-NORMAL_ROLL,NORMAL_ROLL);
            if(recoveringTicks>0&&supports.count()<3) { targetPitch=0;targetRoll=0; }
            pitch=Mth.lerp(.3F,pitch,targetPitch);roll=Mth.lerp(.3F,roll,targetRoll);
            WagonPose tilted=new WagonPose(old.position(),yaw,pitch,roll);
            double required=Double.NEGATIVE_INFINITY;
            for(int i=0;i<4;i++)if(supports.has(i))required=Math.max(required,supports.heights[i]-(wagon.wheelCentre(i,tilted).y-radius(i)-old.position().y));
            dy=Mth.clamp(required-old.position().y,-.16,.30);
            // Small chassis clearance may bridge a diagonal stair corner, but cannot
            // accumulate into climbing walls: the ceiling is tied to wheel support.
            if(supports.count()>=2)stepLiftCeiling=Math.min(required+.25,old.position().y+.30);
            verticalSpeed=0;pitchVelocity=rollVelocity=0;
        } else {
            verticalSpeed=Math.max(-1.8,verticalSpeed-.08);
            if(driverRecovery) {
                pitch=approachUpright(pitch);roll=approachUpright(roll);
                pitchVelocity=rollVelocity=0;
            } else if(recoveringTicks>0) {
                pitch=Mth.lerp(.2F,pitch,0);roll=Mth.lerp(.2F,roll,0);
            } else {
                if(tipDirection!=0)pitchVelocity=Mth.clamp(pitchVelocity-.012F*tipDirection,-.09F,.09F);
                if(!supports.has(0)&&!supports.has(2)&&supports.count()>0)rollVelocity-=.006F;
                if(!supports.has(1)&&!supports.has(3)&&supports.count()>0)rollVelocity+=.006F;
                if(Math.abs(pitch)<=1.22F)pitch=Mth.clamp(pitch+pitchVelocity,-1.22F,1.22F);
                if(Math.abs(roll)<=.85F)roll=Mth.clamp(roll+rollVelocity,-.85F,.85F);
            }
            dy=verticalSpeed;
        }
        Vec3 requested=new Vec3(horizontal.x,dy,horizontal.z);
        if(!falling&&horizontal.lengthSqr()>1e-12)
            wagon.prepareUphillShafts(new WagonPose(old.position().add(requested),yaw,pitch,roll));
        Vec3 before=wagon.position();
        boolean landedOnLowerGround=move(wagon,requested,yaw,pitch,roll);
        Vec3 actual=wagon.position().subtract(before);wagon.setDeltaMovement(actual);
        if(!falling)drive.acceptMovement(actual.x*direction.x+actual.z*direction.z);
        // Re-sample the accepted location: a wall can reject the predicted horizontal step.
        if(!falling)sampleSupport(wagon,wagon.pose(),0,contactHeights);
        else java.util.Arrays.fill(contactHeights,Double.NaN);
        contactPosition=wagon.position();
        if(falling) {
            // A landing must have nearby contact, not merely a distant raycast hit.
            Support landed=support(wagon,wagon.pose());
            boolean verticalContact=dy<-.001 && actual.y>dy+.01;
            if(verticalContact&&landedOnLowerGround) {
                // A shaft or corner can touch first. Keep gravity and momentum until the wheels settle.
                recoveringTicks=20;pitchVelocity=rollVelocity=0;
            }
            if((recoveringTicks>0||driverRecovery)&&verticalContact&&landed.count()>=3&&Math.abs(wagon.pitch())<.15&&Math.abs(wagon.roll())<.15&&uprightClear(wagon)) {
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
    private static float approachUpright(float angle) {
        return Mth.approach(angle,0,.065F);
    }
    /** Body contact also counts when the wheels point sideways or upwards. */
    private static boolean touchingGround(WagonEntity wagon) {
        for(int i=0;i<4;i++) {
            Vec3 foot=wagon.wheelCentre(i,wagon.pose()).add(0,-radius(i),0);
            Ground ground=ground(wagon.level(),foot,.03,.18,.10);
            if(ground.present()&&!ground.forbidden())return true;
        }
        var boxes=wagon.colliders();AABB bounds=bounds(boxes);
        for(VoxelShape shape:wagon.level().getBlockCollisions(wagon,bounds.inflate(.02).expandTowards(0,-.18,0)))for(AABB block:shape.toAabbs()) {
            var terrain=OrientedBox.of(block);
            for(var box:boxes)if(terrain.sweep(box,new Vec3(0,-.18,0))!=null)return true;
        }
        return false;
    }
    private static boolean uprightClear(WagonEntity wagon) {
        var boxes=wagon.motionCollidersAt(new WagonPose(wagon.position(),wagon.getYRot(),0,0));
        AABB bounds=bounds(boxes);
        for(VoxelShape shape:wagon.level().getBlockCollisions(wagon,bounds))for(AABB block:shape.toAabbs())
            if(boxes.stream().anyMatch(box->box.intersects(block)))return false;
        return true;
    }
    /** Swept translation with bounded rotation increments; decorative meshes are never queried. */
    private boolean move(WagonEntity wagon,Vec3 motion,float yaw,float pitch,float roll) {
        if(motion.lengthSqr()<1e-12&&Math.abs(Mth.wrapDegrees(yaw-wagon.getYRot()))<1e-6
            &&Math.abs(pitch-wagon.pitch())<1e-6&&Math.abs(roll-wagon.roll())<1e-6)return false;
        WagonPose previous=wagon.pose();
        try {
            wagon.crowd().begin(motion);
            boolean landed=moveVehicle(wagon,motion,yaw,pitch,roll);
            wagon.crowd().clear(previous);
            return landed;
        }
        finally { wagon.crowd().end(); }
    }
    private boolean moveVehicle(WagonEntity wagon,Vec3 motion,float yaw,float pitch,float roll) {
        boolean landedOnLowerGround=false;
        Ground floorBelow=falling?ground(wagon.level(),wagon.position().add(motion.x,0,motion.z),.5,16,.10):new Ground(Double.NEGATIVE_INFINITY,false);
        double landingHeight=floorBelow.present()&&floorBelow.height<fallStartHeight-1.05?floorBelow.height:Double.NEGATIVE_INFINITY;
        WagonPose start=wagon.pose();
        int steps=Math.max(1,(int)Math.ceil(Math.max(motion.length()/.10,
            Math.max(Math.abs(pitch-start.pitch()),Math.abs(roll-start.roll()))/.035)));
        steps=Math.min(32,steps);
        Vec3 step=motion.scale(1.0/steps);
        List<OrientedBox> oldBoxes=wagon.motionCollidersAt(start);
        WagonTerrain terrainQuery=new WagonTerrain(wagon,Math.abs(motion.y)>1e-5||Math.abs(pitch)>1e-5||Math.abs(roll)>1e-5);
        for(int n=1;n<=steps;n++) {
            float t=(float)n/steps;
            WagonPose previous=wagon.pose();
            WagonPose rotated=new WagonPose(wagon.position(),Mth.rotLerp(t,start.yaw(),yaw),
                Mth.lerp(t,start.pitch(),pitch),Mth.lerp(t,start.roll(),roll));
            boolean turning=rotated.yaw()!=previous.yaw()||rotated.pitch()!=previous.pitch()||rotated.roll()!=previous.roll();
            List<OrientedBox> boxes=turning?wagon.motionCollidersAt(rotated):oldBoxes;
            AABB bounds=bounds(boxes);
            if(!wagon.level().getWorldBorder().isWithinBounds(bounds.expandTowards(step)))break;
            boolean loaded=true;
            for(int x=Mth.floor(bounds.minX)>>4;x<=Mth.floor(bounds.maxX)>>4;x++)for(int z=Mth.floor(bounds.minZ)>>4;z<=Mth.floor(bounds.maxZ)>>4;z++)
                loaded&=wagon.level().hasChunk(x,z);
            if(!loaded)break;
            double extraLift=Math.max(driverRecovery?.25:0,Math.max(0,stepLiftCeiling-rotated.position().y));
            AABB swept=bounds.minmax(bounds(oldBoxes)).expandTowards(step).inflate(.03).expandTowards(0,extraLift,0);
            List<OrientedBox> terrain=terrainQuery.blocks(swept);
            for(VoxelShape shape:wagon.level().getEntityCollisions(wagon,swept))for(AABB block:shape.toAabbs())terrain.add(OrientedBox.of(block));
            terrain.addAll(WagonCollision.nearby(wagon.level(),wagon,swept));
            double preLift=0;
            if(!falling&&step.y>0) {
                // Lift the old pose safely before trying the new pitch/roll. Testing
                // rotation at the old height would reject legal slope-following turns.
                preLift=limit(Direction.Axis.Y,step.y,oldBoxes,terrain);
                if(preLift>0) {
                    oldBoxes=shift(oldBoxes,0,preLift,0);boxes=turning?shift(boxes,0,preLift,0):oldBoxes;
                    previous=new WagonPose(previous.position().add(0,preLift,0),previous.yaw(),previous.pitch(),previous.roll());
                    rotated=new WagonPose(rotated.position().add(0,preLift,0),rotated.yaw(),rotated.pitch(),rotated.roll());
                }
            }
            boolean blockedRotation=turning&&newOverlap(boxes,oldBoxes,terrain);
            if(driverRecovery) {
                // Roll about the ground contact by lifting the centre only as far as the next
                // small angular increment needs. Ceilings, walls and other vehicles still block it.
                double lift=0;
                for(OrientedBox block:terrain)for(OrientedBox box:boxes)
                    if(box.intersects(block))lift=Math.max(lift,block.escapeDistance(box,new Vec3(0,1,0)));
                if(lift>0)blockedRotation=true;
                if(lift>0&&lift<=.25&&limit(Direction.Axis.Y,lift,oldBoxes,terrain)>=lift-1e-5) {
                    List<OrientedBox> raised=shift(boxes,0,lift,0);boolean clear=true;
                    for(OrientedBox block:terrain)
                        if(raised.stream().anyMatch(box->box.intersects(block)))clear=false;
                    if(clear) { rotated=new WagonPose(rotated.position().add(0,lift,0),rotated.yaw(),rotated.pitch(),rotated.roll());boxes=raised;blockedRotation=false; }
                }
            }
            if(blockedRotation) { rotated=previous;boxes=oldBoxes; }
            double y=limit(Direction.Axis.Y,step.y-preLift,boxes,terrain);
            if(y>step.y+.0001&&step.y<0)for(OrientedBox block:terrain) {
                if(block.bounds().maxY>landingHeight+.05)continue;
                for(OrientedBox box:boxes)if(block.sweep(box,new Vec3(0,step.y,0))!=null)landedOnLowerGround=true;
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
            List<OrientedBox> horizontalBoxes=boxes;
            double x=limit(Direction.Axis.X,step.x,boxes,terrain);boxes=shift(boxes,x,0,0);
            double z=limit(Direction.Axis.Z,step.z,boxes,terrain);
            double allowance=stepLiftCeiling-(rotated.position().y+y);
            if(!falling&&allowance>1e-6&&(Math.abs(x-step.x)>1e-6||Math.abs(z-step.z)>1e-6)) {
                // Retry only the minimum lift needed for a low stair corner. Every
                // phase is swept; ceilings, passengers and structures remain solid.
                List<OrientedBox> target=shift(horizontalBoxes,step.x,0,step.z);
                double lift=0;Vec3 up=new Vec3(0,1,0);
                for(var block:terrain)for(var box:target)if(box.intersects(block))lift=Math.max(lift,block.escapeDistance(box,up));
                if(lift>0&&lift<=allowance&&limit(Direction.Axis.Y,lift,horizontalBoxes,terrain)>=lift-1e-7) {
                    var raised=shift(horizontalBoxes,0,lift,0);
                    double rx=limit(Direction.Axis.X,step.x,raised,terrain);
                    var across=shift(raised,rx,0,0);double rz=limit(Direction.Axis.Z,step.z,across,terrain);
                    if(rx*step.x+rz*step.z>x*step.x+z*step.z+1e-9) { x=rx;z=rz;y+=lift;boxes=across; }
                }
            }
            oldBoxes=shift(boxes,0,0,z);
            wagon.applyPose(new WagonPose(rotated.position().add(x,y,z),rotated.yaw(),rotated.pitch(),rotated.roll()));
        }
        return landedOnLowerGround;
    }
    private static AABB bounds(List<OrientedBox> boxes) {
        AABB bounds=boxes.get(0).bounds();for(var box:boxes)bounds=bounds.minmax(box.bounds());return bounds;
    }
    private static boolean newOverlap(List<OrientedBox> boxes,List<OrientedBox> previous,List<OrientedBox> terrain) {
        for(var block:terrain)if(overlaps(boxes,block)&&!overlaps(previous,block))return true;
        return false;
    }
    private static boolean overlaps(List<OrientedBox> boxes,OrientedBox block) {
        for(var box:boxes)if(box.intersects(block))return true;return false;
    }
    private static List<OrientedBox> shift(List<OrientedBox> boxes,double x,double y,double z) {
        if(x==0&&y==0&&z==0)return boxes;
        Vec3 delta=new Vec3(x,y,z);var shifted=new ArrayList<OrientedBox>(boxes.size());
        for(var box:boxes)shifted.add(box.move(delta));return shifted;
    }
    private static double limit(Direction.Axis axis,double distance,List<OrientedBox> boxes,List<OrientedBox> terrain) {
        if(Math.abs(distance)<1e-12)return distance;
        Vec3 motion=switch(axis) { case X->new Vec3(distance,0,0);case Y->new Vec3(0,distance,0);case Z->new Vec3(0,0,distance); };
        double fraction=1;
        for(OrientedBox box:boxes) {
            AABB sweep=box.bounds().expandTowards(motion).inflate(1e-7);
            for(OrientedBox block:terrain) {
                if(!sweep.intersects(block.bounds())||box.intersects(block))continue;
                var hit=block.sweep(box,motion);if(hit!=null)fraction=Math.min(fraction,hit.time());
                if(fraction==0)return 0;
            }
        }
        return distance*fraction;
    }
}
