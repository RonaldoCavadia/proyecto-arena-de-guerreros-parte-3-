// Weapons.java - COMPLETAMENTE FUNCIONAL
import java.util.Objects;

public final class Weapons {
    private final String name;
    private final int damage;

    public Weapons(String name, int damage) {
        this.name = name;
        this.damage = damage;
    }

    public String getName() {
        return name;
    }

    public int getDamage() {
        return damage;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Weapons weapon = (Weapons) obj;
        return damage == weapon.damage && Objects.equals(name, weapon.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, damage);
    }

    @Override
    public String toString() {
        return name + " (Daño: " + damage + ")";
    }
}