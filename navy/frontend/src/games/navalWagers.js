export const NAVAL_RANK_WAGERS = [10,20,30,50,70,100,150,200,250,320,400,500]
export const defaultNavalWager = level => NAVAL_RANK_WAGERS[Math.max(0,Math.min(11,Number(level || 1)-1))]
