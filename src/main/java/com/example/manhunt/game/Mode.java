package com.example.manhunt.game;

/**
 * 游戏模式。
 * <ul>
 *   <li>{@link #CLASSIC}：原生猎人游戏——单检查点链、全员发光、士气量表；</li>
 *   <li>{@link #BOUNTY}：赏金猎人——多检查点、总里程池、三条命、赏金量表与头号赏金。</li>
 * </ul>
 */
public enum Mode {
    CLASSIC,
    BOUNTY
}
