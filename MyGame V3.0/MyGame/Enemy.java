
public final class Enemy {
    private final String enemyName;
    private final int attackPower;
    private final int attacks;
    private final int hp;
    private final boolean estaVivo;

    public Enemy(String enemyName, int attackPower, int attacks) {
        this(enemyName, attackPower, attacks, 50, true);
    }

    private Enemy(String enemyName, int attackPower, int attacks, int hp, boolean estaVivo) {
        this.enemyName = enemyName;
        this.attackPower = attackPower;
        this.attacks = attacks;
        this.hp = hp;
        this.estaVivo = estaVivo;
    }

    // CORREGIDO: Retorna nuevo Enemy con daño aplicado
    public Enemy takeDamage(int amount) {
        int newHp = hp - amount;
        System.out.println(enemyName + " recibe " + amount + " de daño. HP: " + newHp);
        
        if (newHp <= 0) {
            System.out.println("¡" + enemyName + " ha sido derrotado!");
            return new Enemy(enemyName, attackPower, attacks, 0, false);
        }
        return new Enemy(enemyName, attackPower, attacks, newHp, true);
    }

    public Enemy reset() {
        return new Enemy(enemyName, attackPower, attacks, 50, true);
    }

    public String getEnemyName() {
        return enemyName;
    }

    public boolean estaVivo() {
        return estaVivo && hp > 0;
    }

    public int getHp() {
        return hp;
    }

    public int getAttackPower() {
        return attackPower;
    }
}