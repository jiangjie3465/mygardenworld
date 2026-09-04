package com.silkage.mygardenworld.feature.workspace

import com.mygardenworld.v1.BasicPolicy
import com.mygardenworld.v1.BasicTaskPolicy
import com.mygardenworld.v1.BenefitPolicy
import com.mygardenworld.v1.CultivatePolicy
import com.mygardenworld.v1.CustomerOrderPolicy
import com.mygardenworld.v1.FlowerArtPolicy
import com.mygardenworld.v1.OrderPolicy
import com.mygardenworld.v1.PalaceOrderPolicy
import com.mygardenworld.v1.PearlPolicy
import com.mygardenworld.v1.PlantPolicy
import com.mygardenworld.v1.PlantingPolicy
import com.mygardenworld.v1.Policy
import com.mygardenworld.v1.ReputationPolicy
import com.mygardenworld.v1.ResidentOrderPolicy
import com.mygardenworld.v1.SignPolicy
import com.mygardenworld.v1.TeamOrderPolicy
import com.mygardenworld.v1.UnionPolicy
import com.mygardenworld.v1.UnionBuildPolicy
import com.mygardenworld.v1.UnionFlowerPolicy
import com.mygardenworld.v1.UnionLandPolicy
import com.mygardenworld.v1.UnionRacePolicy
import com.mygardenworld.v1.ActivityPolicy
import com.mygardenworld.v1.CyclicNotePolicy
import com.mygardenworld.v1.CyclicStoryPolicy

/**
 * Immutable update helpers for nested policy messages. Java Lite builders do
 * not expose nested builders, so each helper rebuilds the path explicitly.
 */
object PolicyLens {
    inline fun Policy.basic(block: BasicPolicy.Builder.() -> Unit): Policy =
        toBuilder().setBasic(basic.toBuilder().apply(block)).build()

    inline fun Policy.plant(block: PlantPolicy.Builder.() -> Unit): Policy =
        toBuilder().setPlant(plant.toBuilder().apply(block)).build()

    inline fun Policy.order(block: OrderPolicy.Builder.() -> Unit): Policy =
        toBuilder().setOrder(order.toBuilder().apply(block)).build()

    inline fun Policy.reputation(block: ReputationPolicy.Builder.() -> Unit): Policy = basic { setReputation(reputation.toBuilder().apply(block)) }
    inline fun Policy.task(block: BasicTaskPolicy.Builder.() -> Unit): Policy = basic { setTask(task.toBuilder().apply(block)) }
    inline fun Policy.benefit(block: BenefitPolicy.Builder.() -> Unit): Policy = basic { setBenefit(benefit.toBuilder().apply(block)) }
    inline fun Policy.sign(block: SignPolicy.Builder.() -> Unit): Policy = basic { setSign(sign.toBuilder().apply(block)) }
    inline fun Policy.pearl(block: PearlPolicy.Builder.() -> Unit): Policy = basic { setPearl(pearl.toBuilder().apply(block)) }

    inline fun Policy.planting(block: PlantingPolicy.Builder.() -> Unit): Policy = plant { setPlanting(planting.toBuilder().apply(block)) }
    inline fun Policy.cultivate(block: CultivatePolicy.Builder.() -> Unit): Policy = plant { setCultivate(cultivate.toBuilder().apply(block)) }

    inline fun Policy.customer(block: CustomerOrderPolicy.Builder.() -> Unit): Policy = order { setCustomer(customer.toBuilder().apply(block)) }
    inline fun Policy.resident(block: ResidentOrderPolicy.Builder.() -> Unit): Policy = order { setResident(resident.toBuilder().apply(block)) }
    inline fun Policy.palace(block: PalaceOrderPolicy.Builder.() -> Unit): Policy = order { setPalace(palace.toBuilder().apply(block)) }
    inline fun Policy.team(block: TeamOrderPolicy.Builder.() -> Unit): Policy = order { setTeam(team.toBuilder().apply(block)) }
    inline fun Policy.flowerArt(block: FlowerArtPolicy.Builder.() -> Unit): Policy = order { setFlowerArt(flowerArt.toBuilder().apply(block)) }

    inline fun Policy.union(block: UnionPolicy.Builder.() -> Unit): Policy = toBuilder().setUnion(union.toBuilder().apply(block)).build()
    inline fun Policy.unionLand(block: UnionLandPolicy.Builder.() -> Unit): Policy = union { setLand(land.toBuilder().apply(block)) }
    inline fun Policy.unionBuild(block: UnionBuildPolicy.Builder.() -> Unit): Policy = union { setBuild(build.toBuilder().apply(block)) }
    inline fun Policy.unionFlower(block: UnionFlowerPolicy.Builder.() -> Unit): Policy = union { setFlower(flower.toBuilder().apply(block)) }
    inline fun Policy.unionRace(block: UnionRacePolicy.Builder.() -> Unit): Policy = union { setRace(race.toBuilder().apply(block)) }

    inline fun Policy.activity(block: ActivityPolicy.Builder.() -> Unit): Policy = toBuilder().setActivity(activity.toBuilder().apply(block)).build()
    inline fun Policy.cyclicNote(block: CyclicNotePolicy.Builder.() -> Unit): Policy = activity { setCyclicNote(cyclicNote.toBuilder().apply(block)) }
    inline fun Policy.cyclicStory(block: CyclicStoryPolicy.Builder.() -> Unit): Policy = activity { setCyclicStory(cyclicStory.toBuilder().apply(block)) }
}
