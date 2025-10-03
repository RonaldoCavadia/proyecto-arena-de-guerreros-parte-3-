import java.util.function.BiFunction;

public final class Player {
    private final String name;
    private final int hp;
    private final Weapons weapon;
    private final int MAX_HP = 100;

    public Player(String name, int hp, Weapons weapon) {
        this.name = name;
        this.hp = Math.min(hp, MAX_HP);
        this.weapon = weapon;
    }

    // CORREGIDO: Retorna nuevo Player con daño aplicado
    public Player takeDamage(int amount) {
        int newHp = Math.max(0, hp - amount);
        System.out.println(name + " recibe " + amount + " de daño. HP: " + newHp);
        return new Player(name, newHp, weapon);
    }

    // CORREGIDO: Retorna nuevo Player curado
    public Player heal(int amount) {
        int newHp = Math.min(MAX_HP, hp + amount);
        System.out.println(name + " se curó +" + amount + " HP. Vida actual: " + newHp);
        return new Player(name, newHp, weapon);
    }

    // CORREGIDO: Función que ataca enemigos
    public BiFunction<Player, Enemy, Enemy> attackEnemy() {
        return (player, enemy) -> {
            int damage = weapon.getDamage();
            System.out.println(name + " ataca con " + weapon.getName() + 
                             " y causa " + damage + " de daño a " + enemy.getEnemyName());
            return enemy.takeDamage(damage);
        };
    }

    public boolean isAlive() {
        return hp > 0;
    }

    public int getHp() {
        return hp;
    }

    public String getName() {
        return name;
    }

    public Weapons getWeapon() {
        return weapon;
    }
}