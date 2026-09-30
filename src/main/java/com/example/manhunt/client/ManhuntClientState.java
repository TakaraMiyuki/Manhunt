package com.example.manhunt.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * 客户端本地角色状态：由 ManhuntRolePayload 每秒刷新，
 * 驱动技能槽边框、技能轮盘、士气条等本地 UI。
 */
public final class ManhuntClientState {
    private ManhuntClientState() {}

    private static volatile boolean participant;
    private static volatile boolean runner;
    private static volatile boolean skillReady;
    private static volatile int morale;
    private static volatile int moraleRewards;
    /** 技能库全部卡牌物品 id（服务端同步，用于技能轮盘）。 */
    private static volatile List<String> skillIds = List.of();
    /** 当前激活的卡在技能库中的下标。 */
    private static volatile int skillActive;
    /** 逃跑倒计时期间（士气条让位倒计时 bossbar）。 */
    private static volatile boolean escapePhase;
    /** 赏金猎人模式（量表显示为赏金、金色）。 */
    private static volatile boolean bountyMode;
    /** 超级疾跑开启中。 */
    private static volatile boolean sprinting;
    /** 赏金猎人数（量表阈值表选择）。 */
    private static volatile int hunters;
    /** 复活倒计时秒数（-1 = 无）。 */
    private static volatile int respawnSeconds = -1;

    public static void update(boolean isParticipant, boolean isRunner, boolean hasReadySkill,
                              int moraleValue, int moraleRewardCount,
                              List<String> skillIdList, int activeIndex, boolean escape,
                              boolean bounty, boolean sprint, int hunterCount, int respawnIn) {
        participant = isParticipant;
        runner = isRunner;
        skillReady = hasReadySkill;
        morale = moraleValue;
        moraleRewards = moraleRewardCount;
        skillIds = List.copyOf(skillIdList);
        skillActive = activeIndex;
        escapePhase = escape;
        bountyMode = bounty;
        sprinting = sprint;
        hunters = hunterCount;
        respawnSeconds = respawnIn;
    }

    public static boolean isParticipant() {
        return participant;
    }

    public static boolean isRunner() {
        return runner;
    }

    /** 技能库中是否存在任一冷却完毕的卡。 */
    public static boolean isSkillReady() {
        return skillReady;
    }

    /** 当前全队士气（猎人）。 */
    public static int morale() {
        return morale;
    }

    /** 已达成的士气档数（猎人）。 */
    public static int moraleRewards() {
        return moraleRewards;
    }

    /** 技能库卡牌物品 id（服务端同步）。 */
    public static List<String> skillIds() {
        return skillIds;
    }

    /** 当前激活下标。 */
    public static int skillActive() {
        return skillActive;
    }

    /** 技能库卡牌堆（按同步 id 本地构建，用于轮盘渲染）。 */
    public static List<ItemStack> skillStacks() {
        List<ItemStack> out = new ArrayList<>();
        for (String id : skillIds) {
            try {
                var holder = net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .get(Identifier.parse(id));
                if (holder.isPresent()) {
                    out.add(new ItemStack(holder.get().value()));
                }
            } catch (Exception ignored) {
            }
        }
        return out;
    }

    /** 是否处于逃跑倒计时期间（士气条隐藏避让）。 */
    public static boolean isEscapePhase() {
        return escapePhase;
    }

    /** 是否为赏金猎人模式。 */
    public static boolean isBountyMode() {
        return bountyMode;
    }

    /** 超级疾跑是否开启。 */
    public static boolean isSprinting() {
        return sprinting;
    }

    /** 赏金猎人数（赏金量表阈值表）。 */
    public static int hunters() {
        return hunters;
    }

    /** 复活倒计时秒数（-1 = 无）。 */
    public static int respawnSeconds() {
        return respawnSeconds;
    }

    public static void clear() {
        participant = false;
        runner = false;
        skillReady = false;
        morale = 0;
        moraleRewards = 0;
        skillIds = List.of();
        skillActive = -1;
        escapePhase = false;
        bountyMode = false;
        sprinting = false;
        hunters = 0;
        respawnSeconds = -1;
    }
}
